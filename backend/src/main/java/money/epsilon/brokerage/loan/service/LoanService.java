package money.epsilon.brokerage.loan.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.loan.domain.LoanEntry;
import money.epsilon.brokerage.loan.domain.LoanEntry.Details;
import money.epsilon.brokerage.loan.repository.LoanEntryRepository;
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
 * Loan payouts (one entry per payout, R-80). The revenue is worked out on every save with the Loans GST % of the entry's
 * month from Products &amp; taxes; the TDS % is the entry's own (the form starts from the month's Loans TDS %). Region and RD
 * come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class LoanService {

    static final String AUDIT = "LOAN_ENTRY";

    private final LoanEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    LoanService(LoanEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
                ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    /**
     * Search across all months, newest first (R-77). Every part is optional: month range, text (client name, PAN, code,
     * lender, loan type), RM, referral / sub-broker, lender, payment status; cancelled entries only when asked for.
     */
    @Transactional(readOnly = true)
    public List<LoanView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<LoanEntry> found = entries.findByMonthBetweenOrderByMonthDescDisbursalDateDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == LoanEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.payment() == null || f.payment() == e.getPaymentStatus())
                .filter(e -> f.lender() == null || f.lender().isBlank() || Text.key(e.getLender()).equals(Text.key(f.lender())))
                .toList();
        List<LoanView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.lender(), q)
                || contains(v.loanType(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    /** What the Loans list can be narrowed by; every part optional. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String lender, LoanEntry.PaymentStatus payment,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public LoanView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /**
     * The month's active loan payouts as processing needs them (R-74, R-80): gross = invoice (incl. GST), gst, net = the
     * payout excl. GST, and the referral / sub-broker chosen on the entry.
     */
    @Transactional(readOnly = true)
    public List<LoanForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByDisbursalDateAscIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == LoanEntry.Status.ACTIVE)
                .map(e -> new LoanForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record LoanForLedger(long loanEntryId, long clientId, Long partnerId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** What the END file shows for each loan payout: lender, loan type, sanctioned amount. */
    @Transactional(readOnly = true)
    public Map<Long, LoanLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, LoanLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(),
                new LoanLabel(e.getLender(), e.getLoanType(), e.getPayoutKind() == LoanEntry.PayoutKind.TRAIL, e.getSanctionedAmount())));
        return result;
    }

    public record LoanLabel(String lender, String loanType, boolean trail, BigDecimal sanctionedAmount) {
    }

    /** Loan payouts not received yet (the to-do on the Dashboard). */
    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndPaymentStatusIn(LoanEntry.Status.ACTIVE, EnumSet.of(LoanEntry.PaymentStatus.PENDING));
    }

    /** What earlier entries used, so the form can suggest it: lenders, loan types, and the payout % last used per lender + type. */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        LinkedHashSet<String> lenders = new LinkedHashSet<>();
        LinkedHashSet<String> types = new LinkedHashSet<>();
        Map<String, PayoutUsed> payouts = new LinkedHashMap<>();
        for (LoanEntry e : entries.findTop500ByStatusOrderByIdDesc(LoanEntry.Status.ACTIVE)) {
            lenders.add(e.getLender());
            types.add(e.getLoanType());
            if (e.getPayoutPercent() != null) {
                payouts.putIfAbsent(Text.key(e.getLender()) + "|" + Text.key(e.getLoanType()) + "|" + e.getPayoutKind(),
                        new PayoutUsed(e.getLender(), e.getLoanType(), e.getPayoutKind(), e.getPayoutOn(), e.getPayoutPercent()));
            }
        }
        return new Suggestions(lenders.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                types.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), List.copyOf(payouts.values()));
    }

    public record PayoutUsed(String lender, String loanType, LoanEntry.PayoutKind payoutKind, LoanEntry.PayoutOn payoutOn,
                             BigDecimal payoutPercent) {
    }

    public record Suggestions(List<String> lenders, List<String> loanTypes, List<PayoutUsed> payouts) {
    }

    /** The entries found as an Excel sheet in the LOANS register's column order. */
    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return LoanWorkbook.build(search(f));
    }

    /**
     * A new loan payout. The client is an existing one ({@code clientId}) or a new one (name + PAN), added with the entry's
     * RM from the entry's month, as on the Clients screen.
     */
    @Transactional
    public LoanView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "Loan entry");
        }
        LoanEntry e = new LoanEntry(client);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        LoanView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public LoanView update(long id, Details d) {
        LoanEntry e = find(id);
        if (e.getStatus() == LoanEntry.Status.CANCELLED) throw new RuleViolationException("This loan entry is cancelled");
        Details details = check(d);
        LoanView before = get(id);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        LoanView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        LoanEntry e = find(id);
        if (e.getStatus() == LoanEntry.Status.CANCELLED) return;
        LoanView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private Details check(Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (Text.clean(d.lender()) == null) throw new IllegalArgumentException("Enter the lender (e.g. TATA)");
        if (Text.clean(d.loanType()) == null) throw new IllegalArgumentException("Enter the loan type (e.g. Home Loan)");
        if (d.disbursalDate() == null) throw new IllegalArgumentException("Enter the disbursal date");
        if (d.sanctionedAmount() == null || d.sanctionedAmount().signum() <= 0) throw new IllegalArgumentException("Enter the loan / sanctioned amount");
        if (d.utilisedAmount() != null && d.utilisedAmount().signum() < 0) throw new IllegalArgumentException("The utilised amount cannot be negative");
        if (d.payoutOn() == LoanEntry.PayoutOn.UTILISED && d.utilisedAmount() == null) {
            throw new IllegalArgumentException("Enter the utilised amount: the payout % is on it");
        }
        if (d.ratePercent() != null) requirePercent(d.ratePercent(), "Rate of interest");
        if (d.payoutPercent() != null) requirePercent(d.payoutPercent(), "Payout");
        if (d.fixedAmount() != null && d.fixedAmount().signum() < 0) throw new IllegalArgumentException("The fixed amount cannot be negative");
        if (d.tdsApplied()) requirePercent(d.tdsPercent(), "TDS");
        if (d.paymentStatus() != LoanEntry.PaymentStatus.RECEIVED && d.receivedOn() != null) {
            throw new IllegalArgumentException("A received date needs the payment status Received");
        }
        return new Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.lender()), Text.clean(d.loanType()), d.disbursalDate(), d.sanctionedAmount(), d.utilisedAmount(), d.ratePercent(),
                d.payoutKind(), d.payoutOn(), d.payoutPercent(), d.fixedAmount(), d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO,
                d.invoiceRaised(), d.paymentStatus(), Text.clean(d.paymentNote()), d.receivedOn(), d.loanClosed(), Text.clean(d.remarks()), d.given());
    }

    private static void requirePercent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException("Enter the " + what.toLowerCase() + " %");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
    }

    private BigDecimal gstPercent(LocalDate month) {
        return taxes.on(Product.LOAN, month).gstPercent();
    }

    private LoanEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("Loan entry " + id + " not found"));
    }

    private List<LoanView> views(List<LoanEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(LoanEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(LoanEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = new HashMap<>();
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.computeIfAbsent(e.getClientId(), id -> clients.get(id).client().iwellCode());
            return new LoanView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getLender(), e.getLoanType(), e.getDisbursalDate(), e.getSanctionedAmount(),
                    e.getUtilisedAmount(), e.getRatePercent(), e.getPayoutKind(), e.getPayoutOn(), e.getPayoutPercent(), e.getFixedAmount(),
                    e.getGross(), e.isTdsApplied(), e.getTdsPercent(), e.getTds(), e.getGstPercent(), e.getGst(), e.getNet(), e.getInvoice(),
                    e.isFiguresAsGiven(), e.isInvoiceRaised(), e.getPaymentStatus(), e.getPaymentNote(), e.getReceivedOn(), e.isLoanClosed(),
                    e.getRemarks(), e.getStatus(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
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

    private static Map<String, Object> auditView(LoanView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("loan", v.lender() + " " + v.loanType() + (v.payoutKind() == LoanEntry.PayoutKind.TRAIL ? " (trail)" : ""));
        m.put("sanctioned", v.sanctionedAmount());
        m.put("utilised", v.utilisedAmount());
        m.put("payout", v.payoutPercent());
        m.put("gross", v.gross());
        m.put("tds", v.tds());
        m.put("gst", v.gst());
        m.put("net", v.net());
        m.put("payment", v.paymentStatus());
        m.put("loanClosed", v.loanClosed());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record LoanView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                           Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan, String clientType,
                           String mobile, String email, String address, String lender, String loanType, LocalDate disbursalDate,
                           BigDecimal sanctionedAmount, BigDecimal utilisedAmount, BigDecimal ratePercent, LoanEntry.PayoutKind payoutKind,
                           LoanEntry.PayoutOn payoutOn, BigDecimal payoutPercent, BigDecimal fixedAmount, BigDecimal gross, boolean tdsApplied,
                           BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst, BigDecimal net, BigDecimal invoice,
                           boolean figuresAsGiven, boolean invoiceRaised, LoanEntry.PaymentStatus paymentStatus, String paymentNote,
                           LocalDate receivedOn, boolean loanClosed, String remarks, LoanEntry.Status status, String createdBy,
                           Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
