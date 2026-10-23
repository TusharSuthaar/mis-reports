package money.epsilon.brokerage.preipo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.intermediary.service.IntermediaryService;
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
import money.epsilon.brokerage.preipo.domain.PreIpoEntry;
import money.epsilon.brokerage.preipo.domain.PreIpoEntry.Details;
import money.epsilon.brokerage.preipo.domain.PreIpoScrip;
import money.epsilon.brokerage.preipo.repository.PreIpoEntryRepository;
import money.epsilon.brokerage.preipo.repository.PreIpoScripRepository;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.tax.service.ProductTaxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-IPO deals (R-93): buys and buybacks of unlisted shares, one per transaction. Revenue is worked out on every save from
 * the client and vendor prices and the Pre-IPO GST % of the deal's month. Region and RD come from the RM's place in the org
 * that month. Every change is audited.
 */
@Service
public class PreIpoService {

    static final String AUDIT = "PREIPO_ENTRY";

    private final PreIpoEntryRepository entries;
    private final PreIpoScripRepository scrips;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final IntermediaryService platforms;
    private final ProductTaxService taxes;
    private final AuditService audit;

    PreIpoService(PreIpoEntryRepository entries, PreIpoScripRepository scrips, ClientService clients, EmployeeService employees,
                  OrgChartService org, PartnerService partners, IntermediaryService platforms, ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.scrips = scrips;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.platforms = platforms;
        this.taxes = taxes;
        this.audit = audit;
    }

    /**
     * Search across all months, newest first. Every part is optional: month range, text (client name, PAN, code, scrip,
     * ISIN), RM, referral / sub-broker, vendor, buy or buyback, billing status; cancelled deals only when asked for.
     */
    @Transactional(readOnly = true)
    public List<PreIpoView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<PreIpoEntry> found = entries.findByMonthBetweenOrderByMonthDescDealDateDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == PreIpoEntry.Status.ACTIVE)
                .filter(e -> f.kind() == null || f.kind() == e.getKind())
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.vendorId() == null || f.vendorId().equals(e.getVendorId()))
                .filter(e -> f.billing() == null || f.billing() == e.getBillingStatus())
                .toList();
        List<PreIpoView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.scripName(), q)
                || contains(v.isin(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    public record Filter(YearMonth from, YearMonth to, String q, PreIpoEntry.Kind kind, Long rmId, Long partnerId, Long vendorId,
                         PreIpoEntry.BillingStatus billing, boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public PreIpoView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /** Deals not billed and paid yet (the alert on Home). */
    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndBillingStatusIn(PreIpoEntry.Status.ACTIVE,
                EnumSet.of(PreIpoEntry.BillingStatus.TO_CONFIRM, PreIpoEntry.BillingStatus.DEAL_DONE, PreIpoEntry.BillingStatus.BILLED));
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return PreIpoWorkbook.build(search(f));
    }

    /** The month's active deals as processing needs them: gross = invoice (incl. GST), gst, net = brokerage excl. GST. */
    @Transactional(readOnly = true)
    public List<PreIpoForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByDealDateAscIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == PreIpoEntry.Status.ACTIVE && e.getGross().signum() != 0)
                .map(e -> new PreIpoForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getSharingPercent(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record PreIpoForLedger(long preIpoEntryId, long clientId, Long partnerId, BigDecimal sharingPercent, BigDecimal gross, BigDecimal gst,
                                  BigDecimal net) {
    }

    /** What the END file shows for each deal: buy or buyback, scrip, ISIN, amount. */
    @Transactional(readOnly = true)
    public Map<Long, PreIpoLabel> labels(Collection<Long> ids) {
        Map<Long, PreIpoLabel> result = new HashMap<>();
        List<PreIpoEntry> list = entries.findAllById(ids);
        Map<Long, PreIpoScrip> scripById = new HashMap<>();
        scrips.findAllById(list.stream().map(PreIpoEntry::getScripId).collect(Collectors.toSet())).forEach(s -> scripById.put(s.getId(), s));
        list.forEach(e -> {
            PreIpoScrip s = scripById.get(e.getScripId());
            result.put(e.getId(), new PreIpoLabel(e.getKind(), s == null ? null : s.getName(), s == null ? null : s.getIsin(), e.getAmount()));
        });
        return result;
    }

    public record PreIpoLabel(PreIpoEntry.Kind kind, String scripName, String isin, BigDecimal amount) {
    }

    /** A new deal: an existing client ({@code clientId}) or a new one (name + PAN), added with the deal's RM from its month. */
    @Transactional
    public PreIpoView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "Pre-IPO entry");
        }
        PreIpoEntry e = new PreIpoEntry(client);
        e.fill(details, gstPercent(details));
        entries.saveAndFlush(e);
        PreIpoView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public PreIpoView update(long id, Details d) {
        PreIpoEntry e = find(id);
        if (e.getStatus() == PreIpoEntry.Status.CANCELLED) throw new RuleViolationException("This Pre-IPO deal is cancelled");
        Details details = check(d);
        PreIpoView before = get(id);
        e.fill(details, gstPercent(details));
        entries.saveAndFlush(e);
        PreIpoView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        PreIpoEntry e = find(id);
        if (e.getStatus() == PreIpoEntry.Status.CANCELLED) return;
        PreIpoView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Objects.requireNonNullElse(Text.clean(reason), "")));
    }

    private BigDecimal gstPercent(Details d) {
        return taxes.on(Product.PREIPO, d.month()).gstPercent();
    }

    private Details check(Details d) {
        if (d.kind() == null) throw new IllegalArgumentException("Choose buy or buyback");
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (d.sharingPercent() != null) requirePercent(d.sharingPercent(), "Sharing");
        if (!scrips.existsById(d.scripId())) throw new NotFoundException("Pre-IPO scrip " + d.scripId() + " not found");
        platforms.get(d.vendorId());
        if (d.dealDate() == null) throw new IllegalArgumentException("Enter the deal date");
        if (d.quantity() == null || d.quantity().signum() <= 0) throw new IllegalArgumentException("Enter the quantity");
        if (d.clientPrice() == null || d.clientPrice().signum() <= 0) throw new IllegalArgumentException("Enter the client price");
        if (d.vendorPrice() == null || d.vendorPrice().signum() <= 0) {
            throw new IllegalArgumentException(d.kind() == PreIpoEntry.Kind.BUY ? "Enter the vendor's base price" : "Enter the price the vendor pays");
        }
        if (d.tdsApplied()) requirePercent(d.tdsPercent(), "TDS");
        return new Details(d.kind(), month, d.rmId(), d.partnerId(), d.sharingPercent(), Text.clean(d.mobile()), Text.clean(d.email()),
                Text.clean(d.dpName()), Text.clean(d.dpId()), Text.clean(d.dematClientId()), d.scripId(), d.vendorId(), d.dealDate(), d.quantity(),
                d.clientPrice(), d.vendorPrice(), d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, d.portalStatus(),
                d.billingStatus(), d.dealSheet(), Text.clean(d.remarks()));
    }

    private static void requirePercent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException("Enter the " + what.toLowerCase() + " %");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
    }

    private PreIpoEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("Pre-IPO deal " + id + " not found"));
    }

    private List<PreIpoView> views(List<PreIpoEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(PreIpoEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        Map<Long, String> codes = clients.codes(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(PreIpoEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<Long, String> vendorNames = new HashMap<>();
        list.stream().map(PreIpoEntry::getVendorId).distinct().forEach(id -> vendorNames.put(id, platforms.get(id).name()));
        Map<Long, PreIpoScrip> scripById = new HashMap<>();
        scrips.findAllById(list.stream().map(PreIpoEntry::getScripId).collect(Collectors.toSet())).forEach(s -> scripById.put(s.getId(), s));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            String code = codes.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            PreIpoScrip s = scripById.get(e.getScripId());
            return new PreIpoView(e.getId(), e.getKind(), YearMonth.from(e.getMonth()), e.getRmId(),
                    place == null ? employees.get(e.getRmId()).name() : place.rm(), place == null ? null : place.branch(),
                    place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(), e.getSharingPercent(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getDpName(), e.getDpId(), e.getDematClientId(),
                    e.getScripId(), s == null ? null : s.getName(), s == null ? null : s.getIsin(), e.getVendorId(), vendorNames.get(e.getVendorId()),
                    e.getDealDate(), e.getQuantity(), e.getClientPrice(), e.getVendorPrice(), e.getMarkupPerShare(), e.getMarkupPercent(),
                    e.getAmount(), e.getBrokerage(), e.getGross(), e.getGstPercent(), e.getGst(), e.getInvoice(), e.isTdsApplied(), e.getTdsPercent(),
                    e.getTds(), e.getNet(), e.getPortalStatus(), e.getBillingStatus(), e.getDealSheet(), e.getRemarks(), e.getStatus(),
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

    private static Map<String, Object> auditView(PreIpoView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", v.kind());
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("sharingPercent", v.sharingPercent());
        m.put("scrip", v.scripName() + (v.isin() == null ? "" : " " + v.isin()));
        m.put("vendor", v.vendorName());
        m.put("quantity", v.quantity());
        m.put("clientPrice", v.clientPrice());
        m.put("vendorPrice", v.vendorPrice());
        m.put("brokerage", v.brokerage());
        m.put("gross", v.gross());
        m.put("tds", v.tds());
        m.put("net", v.net());
        m.put("billing", v.billingStatus());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record PreIpoView(Long id, PreIpoEntry.Kind kind, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                             Long partnerId, String partnerName, String partnerType, BigDecimal sharingPercent, Long clientId, String clientName,
                             String pan, String clientType, String mobile, String email, String dpName, String dpId, String dematClientId,
                             Long scripId, String scripName, String isin, Long vendorId, String vendorName, LocalDate dealDate,
                             BigDecimal quantity, BigDecimal clientPrice, BigDecimal vendorPrice, BigDecimal markupPerShare,
                             BigDecimal markupPercent, BigDecimal amount, BigDecimal brokerage, BigDecimal gross, BigDecimal gstPercent,
                             BigDecimal gst, BigDecimal invoice, boolean tdsApplied, BigDecimal tdsPercent, BigDecimal tds, BigDecimal net,
                             PreIpoEntry.PortalStatus portalStatus, PreIpoEntry.BillingStatus billingStatus, PreIpoEntry.DealSheet dealSheet,
                             String remarks, PreIpoEntry.Status status, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
