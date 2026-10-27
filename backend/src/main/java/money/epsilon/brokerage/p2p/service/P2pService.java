package money.epsilon.brokerage.p2p.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
import money.epsilon.brokerage.p2p.domain.P2pEntry;
import money.epsilon.brokerage.p2p.repository.P2pEntryRepository;
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.tax.service.ProductTaxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P2P investments (R-87), entry only for now: as in the P2P register, with a typed brokerage when it is known. GST % from
 * Products &amp; taxes for the month. Region and RD come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class P2pService {

    static final String AUDIT = "P2P_ENTRY";

    private final P2pEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    P2pService(P2pEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
               ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    /** {@code received}: true = brokerage received, false = brokerage not received yet, null = all. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String platform, Boolean received,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public List<P2pView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<P2pEntry> found = entries.findByMonthBetweenOrderByMonthDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == P2pEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.platform() == null || f.platform().isBlank() || Text.key(e.getPlatform()).equals(Text.key(f.platform())))
                .filter(e -> f.received() == null || (f.received() ? e.getReceivedOn() != null
                        : e.getReceivedOn() == null && e.getGross().signum() > 0))
                .toList();
        List<P2pView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.platform(), q)
                || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    @Transactional(readOnly = true)
    public P2pView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /** Platforms and schemes used before, for the form's suggestions. */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        LinkedHashSet<String> platforms = new LinkedHashSet<>();
        LinkedHashSet<String> schemes = new LinkedHashSet<>();
        entries.findTop500ByStatusOrderByIdDesc(P2pEntry.Status.ACTIVE).forEach(e -> {
            platforms.add(e.getPlatform());
            schemes.add(e.getScheme());
        });
        return new Suggestions(platforms.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), schemes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    public record Suggestions(List<String> platforms, List<String> schemes) {
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return P2pWorkbook.build(search(f));
    }

    @Transactional
    public P2pView create(Long clientId, String newClientName, String newClientPan, P2pEntry.Details d) {
        P2pEntry.Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "P2P entry");
        }
        P2pEntry e = new P2pEntry(client);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        P2pView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public P2pView update(long id, P2pEntry.Details d) {
        P2pEntry e = find(id);
        if (e.getStatus() == P2pEntry.Status.CANCELLED) throw new RuleViolationException("This P2P entry is cancelled");
        P2pEntry.Details details = check(d);
        P2pView before = get(id);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        P2pView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        P2pEntry e = find(id);
        if (e.getStatus() == P2pEntry.Status.CANCELLED) return;
        P2pView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private P2pEntry.Details check(P2pEntry.Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (Text.clean(d.platform()) == null) throw new IllegalArgumentException("Enter the partner (platform, e.g. INDIA MONEY MART)");
        if (Text.clean(d.scheme()) == null) throw new IllegalArgumentException("Enter the P2P scheme (e.g. YEARLY)");
        if (d.amount() != null && d.amount().signum() <= 0) throw new IllegalArgumentException("The amount invested must be more than 0");
        if (d.roiPercent() != null && (d.roiPercent().signum() < 0 || d.roiPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("ROI % must be between 0 and 100");
        }
        if (d.brokerage() != null && d.brokerage().signum() < 0) throw new IllegalArgumentException("The brokerage cannot be negative");
        if (d.tdsApplied() && (d.tdsPercent() == null || d.tdsPercent().signum() < 0 || d.tdsPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("TDS % must be between 0 and 100");
        }
        return new P2pEntry.Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.platform()), Text.clean(d.scheme()), d.roiPercent(), d.amount(), d.fundsInitiatedOn(), d.startDate(), d.quarterEndDate(),
                d.onPortal(), d.brokerage(), d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, Text.clean(d.paymentNote()),
                d.receivedOn(), Text.clean(d.revSharingStatus()), Text.clean(d.remarks()));
    }

    private BigDecimal gstPercent(LocalDate month) {
        return taxes.on(Product.P2P, month).gstPercent();
    }

    private P2pEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("P2P entry " + id + " not found"));
    }

    private List<P2pView> views(List<P2pEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(P2pEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(P2pEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.get(e.getClientId());
            return new P2pView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getPlatform(), e.getScheme(), e.getRoiPercent(), e.getAmount(),
                    e.getFundsInitiatedOn(), e.getStartDate(), e.getQuarterEndDate(), e.isOnPortal(), e.getBrokerage(), e.getGross(),
                    e.isTdsApplied(), e.getTdsPercent(), e.getTds(), e.getGstPercent(), e.getGst(), e.getNet(), e.getInvoice(), e.getPaymentNote(),
                    e.getReceivedOn(), e.getRevSharingStatus(), e.getRemarks(), e.getStatus(), e.getCreatedBy(), e.getCreatedAt(),
                    e.getUpdatedBy(), e.getUpdatedAt());
        }).toList();
    }

    private static Map<Long, Place> places(OrgChart chart) {
        Map<Long, Place> result = new HashMap<>();
        chart.regions().forEach(r -> r.branches().forEach(b -> b.people().forEach(p ->
                result.put(p.id(), new Place(p.name(), b.name(), r.name(), r.rd() == null ? null : r.rd().name())))));
        chart.branchesWithoutRegion().forEach(b -> b.people().forEach(p -> result.put(p.id(), new Place(p.name(), b.name(), null, null))));
        return result;
    }

    private static Map<String, Object> auditView(P2pView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("p2p", v.platform() + " · " + v.scheme());
        m.put("amount", v.amount());
        m.put("roi", v.roiPercent());
        m.put("brokerage", v.brokerage());
        m.put("payment", v.paymentNote());
        m.put("receivedOn", v.receivedOn());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record P2pView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                          Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan, String clientType,
                          String mobile, String email, String address, String platform, String scheme, BigDecimal roiPercent, BigDecimal amount,
                          LocalDate fundsInitiatedOn, LocalDate startDate, LocalDate quarterEndDate, boolean onPortal, BigDecimal brokerage,
                          BigDecimal gross, boolean tdsApplied, BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst,
                          BigDecimal net, BigDecimal invoice, String paymentNote, LocalDate receivedOn, String revSharingStatus, String remarks,
                          P2pEntry.Status status, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    /** The month's active P2P entries with brokerage, as processing needs them (R-87): gross = invoice, gst, net = brokerage excl. GST. */
    @Transactional(readOnly = true)
    public List<P2pForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == P2pEntry.Status.ACTIVE && e.getGross() != null && e.getGross().signum() != 0)
                .map(e -> new P2pForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record P2pForLedger(long p2pEntryId, long clientId, Long partnerId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** What the END shows for each P2P line: platform and scheme, and the amount invested as AUM. */
    @Transactional(readOnly = true)
    public Map<Long, P2pLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, P2pLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(), new P2pLabel(e.getPlatform() + (e.getScheme() == null ? "" : " · " + e.getScheme()), e.getAmount())));
        return result;
    }

    public record P2pLabel(String item, BigDecimal amount) {
    }
}
