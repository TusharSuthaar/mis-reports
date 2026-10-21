package money.epsilon.brokerage.nps.service;

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
import money.epsilon.brokerage.nps.domain.NpsEntry;
import money.epsilon.brokerage.nps.repository.NpsEntryRepository;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
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
 * NPS investments (R-86), entry only for now: as in the NPS register, with the revenue when there is one. GST % from
 * Products &amp; taxes for the month. Region and RD come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class NpsService {

    static final String AUDIT = "NPS_ENTRY";

    private final NpsEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    NpsService(NpsEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
               ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    /** {@code received}: true = revenue received, false = revenue not received yet, null = all. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String pop, Boolean received,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public List<NpsView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<NpsEntry> found = entries.findByMonthBetweenOrderByMonthDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == NpsEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.pop() == null || f.pop().isBlank() || Text.key(e.getPop()).equals(Text.key(f.pop())))
                .filter(e -> f.received() == null || (f.received() ? e.getReceivedOn() != null
                        : e.getReceivedOn() == null && e.getRevenue().signum() > 0))
                .toList();
        List<NpsView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.pop(), q)
                || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    @Transactional(readOnly = true)
    public NpsView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<String> pops() {
        LinkedHashSet<String> pops = new LinkedHashSet<>();
        entries.findTop500ByStatusOrderByIdDesc(NpsEntry.Status.ACTIVE).forEach(e -> pops.add(e.getPop()));
        return pops.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return NpsWorkbook.build(search(f));
    }

    @Transactional
    public NpsView create(Long clientId, String newClientName, String newClientPan, NpsEntry.Details d) {
        NpsEntry.Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "NPS entry");
        }
        NpsEntry e = new NpsEntry(client);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        NpsView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public NpsView update(long id, NpsEntry.Details d) {
        NpsEntry e = find(id);
        if (e.getStatus() == NpsEntry.Status.CANCELLED) throw new RuleViolationException("This NPS entry is cancelled");
        NpsEntry.Details details = check(d);
        NpsView before = get(id);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        NpsView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        NpsEntry e = find(id);
        if (e.getStatus() == NpsEntry.Status.CANCELLED) return;
        NpsView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private NpsEntry.Details check(NpsEntry.Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (Text.clean(d.pop()) == null) throw new IllegalArgumentException("Enter the partner (POP, e.g. STOCK HOLDING)");
        if (d.investmentDate() == null) throw new IllegalArgumentException("Enter the investment date");
        if (d.amount() == null || d.amount().signum() <= 0) throw new IllegalArgumentException("Enter the investment amount");
        if (d.sharePercent() != null && (d.sharePercent().signum() < 0 || d.sharePercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Sharing % must be between 0 and 100");
        }
        if ((d.popCharges() != null && d.popCharges().signum() < 0) || (d.revenue() != null && d.revenue().signum() < 0)) {
            throw new IllegalArgumentException("Amounts cannot be negative");
        }
        return new NpsEntry.Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.pop()), d.investmentDate(), d.amount(), d.welcomeMail(), d.investmentMail(), d.certificate(), d.onPortal(),
                Text.clean(d.revenueRemarks()), d.popCharges(), d.sharePercent(), d.revenue(), d.receivedOn(), Text.clean(d.remarks()));
    }

    private BigDecimal gstPercent(LocalDate month) {
        return taxes.on(Product.NPS, month).gstPercent();
    }

    private NpsEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("NPS entry " + id + " not found"));
    }

    private List<NpsView> views(List<NpsEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(NpsEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(NpsEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = new HashMap<>();
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.computeIfAbsent(e.getClientId(), id -> clients.get(id).client().iwellCode());
            return new NpsView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getPop(), e.getInvestmentDate(), e.getAmount(), e.isWelcomeMail(),
                    e.isInvestmentMail(), e.getCertificate(), e.isOnPortal(), e.getRevenueRemarks(), e.getPopCharges(), e.getSharePercent(),
                    e.getRevenue(), e.getGstPercent(), e.getGst(), e.getInvoice(), e.getReceivedOn(), e.getRemarks(), e.getStatus(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
        }).toList();
    }

    private static Map<Long, Place> places(OrgChart chart) {
        Map<Long, Place> result = new HashMap<>();
        chart.regions().forEach(r -> r.branches().forEach(b -> b.people().forEach(p ->
                result.put(p.id(), new Place(p.name(), b.name(), r.name(), r.rd() == null ? null : r.rd().name())))));
        chart.branchesWithoutRegion().forEach(b -> b.people().forEach(p -> result.put(p.id(), new Place(p.name(), b.name(), null, null))));
        return result;
    }

    private static Map<String, Object> auditView(NpsView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("pop", v.pop());
        m.put("amount", v.amount());
        m.put("investmentDate", v.investmentDate());
        m.put("revenue", v.revenue());
        m.put("gst", v.gst());
        m.put("receivedOn", v.receivedOn());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record NpsView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                          Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan, String clientType,
                          String mobile, String email, String address, String pop, LocalDate investmentDate, BigDecimal amount,
                          boolean welcomeMail, boolean investmentMail, NpsEntry.Certificate certificate, boolean onPortal, String revenueRemarks,
                          BigDecimal popCharges, BigDecimal sharePercent, BigDecimal revenue, BigDecimal gstPercent, BigDecimal gst,
                          BigDecimal invoice, LocalDate receivedOn, String remarks, NpsEntry.Status status, String createdBy, Instant createdAt,
                          String updatedBy, Instant updatedAt) {
    }
}
