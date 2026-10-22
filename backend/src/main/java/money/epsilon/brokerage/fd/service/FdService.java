package money.epsilon.brokerage.fd.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.fd.domain.FdEntry;
import money.epsilon.brokerage.fd.domain.FdEntry.Details;
import money.epsilon.brokerage.fd.repository.FdEntryRepository;
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
 * FD entries (one per FD booked). The revenue is worked out on every save with the FD GST % of the entry's month from
 * Products &amp; taxes (R-70, R-71). Region and RD come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class FdService {

    static final String AUDIT = "FD_ENTRY";

    private final FdEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    FdService(FdEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
              ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FdView> list(YearMonth month) {
        return views(entries.findByMonthOrderByDepositDateAscIdAsc(month.atDay(1)));
    }

    /**
     * Search across all months, newest first (R-77). Every part is optional: month range, text (client name, PAN, code,
     * FD company, FD no.), RM, referral / sub-broker, platform, payment status; cancelled entries only when asked for.
     */
    @Transactional(readOnly = true)
    public List<FdView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<FdEntry> found = entries.findByMonthBetweenOrderByMonthDescDepositDateDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == FdEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.payment() == null || f.payment() == e.getPaymentStatus())
                .filter(e -> f.platform() == null || f.platform().isBlank() || Text.key(e.getPlatform()).equals(Text.key(f.platform())))
                .toList();
        List<FdView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.fdName(), q)
                || contains(v.fdNo(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    /** What the FD list can be narrowed by; every part optional. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String platform, FdEntry.PaymentStatus payment,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public FdView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /**
     * The month's active FDs as processing needs them (R-74): the client, the referral / sub-broker chosen on the FD, and
     * the amounts — gross = invoice (incl. GST), gst, net = the commission excl. GST.
     */
    @Transactional(readOnly = true)
    public List<FdForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByDepositDateAscIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == FdEntry.Status.ACTIVE)
                .map(e -> new FdForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record FdForLedger(long fdEntryId, long clientId, Long partnerId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** What the END file shows for each FD: company, number, amount. */
    @Transactional(readOnly = true)
    public Map<Long, FdLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, FdLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(), new FdLabel(e.getFdName(), e.getFdNo(), e.getAmount())));
        return result;
    }

    public record FdLabel(String fdName, String fdNo, BigDecimal amount) {
    }

    /** FDs whose commission is not received yet (the alert on Home). */
    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndPaymentStatusIn(FdEntry.Status.ACTIVE,
                java.util.EnumSet.of(FdEntry.PaymentStatus.PENDING, FdEntry.PaymentStatus.RECEIVED_GST_PENDING));
    }

    /**
     * What earlier entries used, so the form can suggest it: FD companies, platforms, and the margin last used for each
     * company + platform + period (newest wins).
     */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        java.util.LinkedHashSet<String> companies = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> platforms = new java.util.LinkedHashSet<>();
        Map<String, MarginUsed> margins = new java.util.LinkedHashMap<>();
        for (FdEntry e : entries.findTop500ByStatusOrderByIdDesc(FdEntry.Status.ACTIVE)) {
            companies.add(e.getFdName());
            if (e.getPlatform() != null) platforms.add(e.getPlatform());
            margins.putIfAbsent(Text.key(e.getFdName()) + "|" + Text.key(e.getPlatform()) + "|" + e.getPeriodMonths(),
                    new MarginUsed(e.getFdName(), e.getPlatform(), e.getPeriodMonths(), e.getMarginPercent()));
        }
        return new Suggestions(companies.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                platforms.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), List.copyOf(margins.values()));
    }

    public record MarginUsed(String fdName, String platform, int periodMonths, BigDecimal marginPercent) {
    }

    public record Suggestions(List<String> fdNames, List<String> platforms, List<MarginUsed> margins) {
    }

    /** The month's FDs as an Excel sheet in the FD register's column order. */
    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return FdWorkbook.build(search(f));
    }

    /**
     * A new FD. The client is an existing one ({@code clientId}) or a new one (name + PAN), added with the entry's RM from
     * the entry's month, as on the Clients screen.
     */
    @Transactional
    public FdView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "FD entry");
        }
        FdEntry e = new FdEntry(client);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        FdView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public FdView update(long id, Details d) {
        FdEntry e = find(id);
        if (e.getStatus() == FdEntry.Status.CANCELLED) throw new RuleViolationException("This FD entry is cancelled");
        Details details = check(d);
        FdView before = get(id);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        FdView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        FdEntry e = find(id);
        if (e.getStatus() == FdEntry.Status.CANCELLED) return;
        FdView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private Details check(Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (d.fdName() == null || d.fdName().isBlank()) throw new IllegalArgumentException("Enter the FD name");
        if (d.interestFrequency() == null) throw new IllegalArgumentException("Choose the interest frequency");
        if (d.depositDate() == null || d.maturityDate() == null) throw new IllegalArgumentException("Enter the deposit and maturity dates");
        if (!d.maturityDate().isAfter(d.depositDate())) throw new IllegalArgumentException("The maturity date must be after the deposit date");
        if (d.periodMonths() < 1 || d.periodMonths() > 600) throw new IllegalArgumentException("Enter the deposit period in months");
        requirePercent(d.ratePercent(), "Interest rate");
        requirePercent(d.marginPercent(), "Margin");
        if (d.tdsApplied()) requirePercent(d.tdsPercent(), "TDS");
        if (d.amount() == null || d.amount().signum() <= 0) throw new IllegalArgumentException("Enter the investment amount");
        if (d.certificate() == null) throw new IllegalArgumentException("Choose the certificate status");
        return new Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.fdName()), Text.clean(d.fdNo()), d.interestFrequency(), Text.clean(d.platform()), d.depositDate(),
                d.periodMonths(), d.maturityDate(), d.ratePercent(), d.amount(), d.compounding(), d.certificate(), d.marginPercent(), d.tdsApplied(),
                d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, d.invoiceRaised(), d.onPortal(), d.paymentStatus(), Text.clean(d.remarks()), d.given());
    }

    private static void requirePercent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException("Enter the " + what.toLowerCase() + " %");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
    }

    private BigDecimal gstPercent(LocalDate month) {
        return taxes.on(Product.FD, month).gstPercent();
    }

    private FdEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("FD entry " + id + " not found"));
    }

    private List<FdView> views(List<FdEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(FdEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(FdEntry::getPartnerId).filter(java.util.Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.get(e.getClientId());
            return new FdView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getFdName(), e.getFdNo(), e.getInterestFrequency(), e.getPlatform(),
                    e.getDepositDate(), e.getPeriodMonths(), e.getMaturityDate(), e.getRatePercent(), e.getAmount(), e.getCompounding(), e.getMaturityValue(), e.getCertificate(),
                    e.getMarginPercent(), e.getGross(), e.isTdsApplied(), e.getTdsPercent(), e.getTds(), e.getGstPercent(), e.getGst(),
                    e.getNet(), e.getInvoice(), e.isFiguresAsGiven(), e.isInvoiceRaised(), e.isOnPortal(), e.getPaymentStatus(), e.getRemarks(), e.getStatus(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
        }).toList();
    }

    /** RM id → where they sit that month. */
    private static Map<Long, Place> places(OrgChart chart) {
        Map<Long, Place> result = new HashMap<>();
        chart.regions().forEach(r -> r.branches().forEach(b -> b.people().forEach(p ->
                result.put(p.id(), new Place(p.name(), b.name(), r.name(), r.rd() == null ? null : r.rd().name())))));
        chart.branchesWithoutRegion().forEach(b -> b.people().forEach(p -> result.put(p.id(), new Place(p.name(), b.name(), null, null))));
        return result;
    }

    private static Map<String, Object> auditView(FdView v) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("fd", v.fdName() + (v.fdNo() == null ? "" : " #" + v.fdNo()));
        m.put("amount", v.amount());
        m.put("maturityValue", v.maturityValue());
        m.put("margin", v.marginPercent());
        m.put("gross", v.gross());
        m.put("tds", v.tds());
        m.put("gst", v.gst());
        m.put("net", v.net());
        m.put("invoiceRaised", v.invoiceRaised());
        m.put("payment", v.paymentStatus());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record FdView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                         Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan, String clientType,
                         String mobile, String email, String address, String fdName, String fdNo,
                         FdEntry.InterestFrequency interestFrequency, String platform, LocalDate depositDate, int periodMonths, LocalDate maturityDate,
                         BigDecimal ratePercent, BigDecimal amount, FdEntry.Compounding compounding, BigDecimal maturityValue,
                         FdEntry.Certificate certificate,
                         BigDecimal marginPercent, BigDecimal gross, boolean tdsApplied, BigDecimal tdsPercent, BigDecimal tds,
                         BigDecimal gstPercent, BigDecimal gst, BigDecimal net, BigDecimal invoice, boolean figuresAsGiven, boolean invoiceRaised, boolean onPortal,
                         FdEntry.PaymentStatus paymentStatus, String remarks, FdEntry.Status status, String createdBy,
                         Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
