package money.epsilon.brokerage.pms.service;

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
import money.epsilon.brokerage.partner.service.PartnerService;
import money.epsilon.brokerage.partner.service.PartnerService.PartnerView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import money.epsilon.brokerage.pms.domain.PmsPayout;
import money.epsilon.brokerage.pms.repository.PmsInvestmentRepository;
import money.epsilon.brokerage.pms.repository.PmsPayoutRepository;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.tax.service.ProductTaxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PMS, entry only (R-85): client investments as in the PMS register, and AMC payouts per invoice period. Neither feeds month
 * processing or the END yet. Region and RD come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class PmsService {

    static final String AUDIT_INVESTMENT = "PMS_INVESTMENT";
    static final String AUDIT_PAYOUT = "PMS_PAYOUT";

    private final PmsInvestmentRepository investments;
    private final PmsPayoutRepository payouts;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    PmsService(PmsInvestmentRepository investments, PmsPayoutRepository payouts, ClientService clients, EmployeeService employees,
               OrgChartService org, PartnerService partners, ProductTaxService taxes, AuditService audit) {
        this.investments = investments;
        this.payouts = payouts;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    // ---- investments ------------------------------------------------------------------------------------------

    /** Every part optional; {@code amc} narrows like a platform; redeemed ones are listed too (shown as such). */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String amc, boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public List<InvestmentView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<PmsInvestment> found = investments.findByMonthBetweenOrderByMonthDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == PmsInvestment.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.amc() == null || f.amc().isBlank() || Text.key(e.getAmc()).equals(Text.key(f.amc())))
                .toList();
        List<InvestmentView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.amc(), q)
                || contains(v.strategy(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    @Transactional(readOnly = true)
    public InvestmentView get(long id) {
        return views(List.of(findInvestment(id))).get(0);
    }

    /** AMCs and strategies used before, for the forms' suggestions. */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        LinkedHashSet<String> amcs = new LinkedHashSet<>();
        LinkedHashSet<String> strategies = new LinkedHashSet<>();
        investments.findTop500ByStatusOrderByIdDesc(PmsInvestment.Status.ACTIVE).forEach(e -> {
            amcs.add(e.getAmc());
            strategies.add(e.getStrategy());
        });
        payouts.findAll().forEach(p -> amcs.add(p.getAmc()));
        return new Suggestions(amcs.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), strategies.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    public record Suggestions(List<String> amcs, List<String> strategies) {
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return PmsWorkbook.build(search(f));
    }

    @Transactional
    public InvestmentView create(Long clientId, String newClientName, String newClientPan, PmsInvestment.Details d) {
        PmsInvestment.Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "PMS entry");
        }
        PmsInvestment e = new PmsInvestment(client);
        e.fill(details);
        investments.saveAndFlush(e);
        InvestmentView view = get(e.getId());
        audit.record(AUDIT_INVESTMENT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public InvestmentView update(long id, PmsInvestment.Details d) {
        PmsInvestment e = findInvestment(id);
        if (e.getStatus() == PmsInvestment.Status.CANCELLED) throw new RuleViolationException("This PMS entry is cancelled");
        PmsInvestment.Details details = check(d);
        InvestmentView before = get(id);
        e.fill(details);
        investments.saveAndFlush(e);
        InvestmentView after = get(id);
        audit.record(AUDIT_INVESTMENT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        PmsInvestment e = findInvestment(id);
        if (e.getStatus() == PmsInvestment.Status.CANCELLED) return;
        InvestmentView before = get(id);
        e.cancel();
        investments.saveAndFlush(e);
        audit.record(AUDIT_INVESTMENT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private PmsInvestment.Details check(PmsInvestment.Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (Text.clean(d.amc()) == null) throw new IllegalArgumentException("Enter the AMC (e.g. ICICI PRU)");
        if (Text.clean(d.strategy()) == null) throw new IllegalArgumentException("Enter the PMS strategy (e.g. ICICI PIPE)");
        if (d.amount() == null || d.amount().signum() <= 0) throw new IllegalArgumentException("Enter the investment amount");
        if (d.investmentDate() == null) throw new IllegalArgumentException("Enter the investment date");
        if (d.trailPercent() != null && (d.trailPercent().signum() < 0 || d.trailPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Trail % must be between 0 and 100");
        }
        if (d.holdingStatus() == PmsInvestment.Holding.REDEEMED) {
            if (d.redeemedOn() == null) throw new IllegalArgumentException("Enter the date it was redeemed");
            if (d.redeemedOn().isBefore(d.investmentDate())) throw new IllegalArgumentException("Redeemed before it was invested");
        }
        return new PmsInvestment.Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.amc()), Text.clean(d.strategy()), d.amount(), d.investmentDate(), d.onPortal(), d.trailPercent(), d.payoutFrequency(),
                d.qty(), d.price(), d.consideration(), d.certificate(), d.investmentMail(), d.holdingStatus(), d.redeemedOn(), Text.clean(d.remarks()));
    }

    private PmsInvestment findInvestment(long id) {
        return investments.findById(id).orElseThrow(() -> new NotFoundException("PMS entry " + id + " not found"));
    }

    /** The PMS entries of these clients (cancelled ones left out), all months, newest first: the client page's holdings (R-09). */
    @Transactional(readOnly = true)
    public List<InvestmentView> forClients(java.util.Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return List.of();
        return views(investments.findByClientIdInOrderByMonthDescIdDesc(clientIds).stream().filter(e -> e.getStatus() == PmsInvestment.Status.ACTIVE).toList());
    }

    private List<InvestmentView> views(List<PmsInvestment> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(PmsInvestment::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(PmsInvestment::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.get(e.getClientId());
            return new InvestmentView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getAmc(), e.getStrategy(), e.getAmount(), e.getInvestmentDate(), e.isOnPortal(),
                    e.getTrailPercent(), e.getPayoutFrequency(), e.getQty(), e.getPrice(), e.getConsideration(), e.getCertificate(), e.isInvestmentMail(),
                    e.getHoldingStatus(), e.getRedeemedOn(), e.getRemarks(), e.getStatus(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(),
                    e.getUpdatedAt());
        }).toList();
    }

    private static Map<Long, Place> places(OrgChart chart) {
        Map<Long, Place> result = new HashMap<>();
        chart.regions().forEach(r -> r.branches().forEach(b -> b.people().forEach(p ->
                result.put(p.id(), new Place(p.name(), b.name(), r.name(), r.rd() == null ? null : r.rd().name())))));
        chart.branchesWithoutRegion().forEach(b -> b.people().forEach(p -> result.put(p.id(), new Place(p.name(), b.name(), null, null))));
        return result;
    }

    private static Map<String, Object> auditView(InvestmentView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("pms", v.amc() + " · " + v.strategy());
        m.put("amount", v.amount());
        m.put("investmentDate", v.investmentDate());
        m.put("trail", v.trailPercent());
        m.put("holding", v.holdingStatus() + (v.redeemedOn() == null ? "" : " " + v.redeemedOn()));
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record InvestmentView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                                 Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan,
                                 String clientType, String mobile, String email, String address, String amc, String strategy, BigDecimal amount,
                                 LocalDate investmentDate, boolean onPortal, BigDecimal trailPercent, PmsInvestment.PayoutFrequency payoutFrequency,
                                 BigDecimal qty, BigDecimal price, BigDecimal consideration, PmsInvestment.Certificate certificate,
                                 boolean investmentMail, PmsInvestment.Holding holdingStatus, LocalDate redeemedOn, String remarks,
                                 PmsInvestment.Status status, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    // ---- AMC payouts ------------------------------------------------------------------------------------------

    /** Payouts whose period starts in the range (all by default), newest first; {@code amc} optional. */
    @Transactional(readOnly = true)
    public List<PayoutView> payouts(YearMonth from, YearMonth to, String amc, boolean includeCancelled) {
        LocalDate a = from == null ? LocalDate.of(2000, 1, 1) : from.atDay(1);
        LocalDate b = to == null ? LocalDate.of(2999, 12, 1) : to.atDay(1);
        return payouts.findByPeriodFromBetweenOrderByPeriodFromDescIdDesc(a, b).stream()
                .filter(p -> includeCancelled || p.getStatus() == PmsPayout.Status.ACTIVE)
                .filter(p -> amc == null || amc.isBlank() || Text.key(p.getAmc()).equals(Text.key(amc)))
                .map(PmsService::view).toList();
    }

    @Transactional
    public PayoutView createPayout(PmsPayout.Details d) {
        PmsPayout.Details details = check(d);
        PmsPayout p = PmsPayout.create();
        p.fill(details, gstPercent(details.periodTo() == null ? details.periodFrom() : details.periodTo()));
        payouts.saveAndFlush(p);
        PayoutView view = view(p);
        audit.record(AUDIT_PAYOUT, p.getId(), "CREATE", null, view);
        return view;
    }

    @Transactional
    public PayoutView updatePayout(long id, PmsPayout.Details d) {
        PmsPayout p = payouts.findById(id).orElseThrow(() -> new NotFoundException("PMS payout " + id + " not found"));
        if (p.getStatus() == PmsPayout.Status.CANCELLED) throw new RuleViolationException("This payout is cancelled");
        PmsPayout.Details details = check(d);
        PayoutView before = view(p);
        p.fill(details, gstPercent(details.periodTo() == null ? details.periodFrom() : details.periodTo()));
        payouts.saveAndFlush(p);
        PayoutView after = view(p);
        audit.record(AUDIT_PAYOUT, id, "UPDATE", before, after);
        return after;
    }

    @Transactional
    public void cancelPayout(long id, String reason) {
        PmsPayout p = payouts.findById(id).orElseThrow(() -> new NotFoundException("PMS payout " + id + " not found"));
        if (p.getStatus() == PmsPayout.Status.CANCELLED) return;
        PayoutView before = view(p);
        p.cancel();
        payouts.saveAndFlush(p);
        audit.record(AUDIT_PAYOUT, id, "CANCEL", before, Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private PmsPayout.Details check(PmsPayout.Details d) {
        if (Text.clean(d.amc()) == null) throw new IllegalArgumentException("Enter the AMC");
        if (d.periodFrom() == null) throw new IllegalArgumentException("Enter the invoice period");
        LocalDate from = d.periodFrom().withDayOfMonth(1);
        LocalDate to = d.periodTo() == null ? from : d.periodTo().withDayOfMonth(1);
        if (to.isBefore(from)) throw new IllegalArgumentException("The period ends before it starts");
        if (d.amount() == null || d.amount().signum() < 0) throw new IllegalArgumentException("Enter the payout amount");
        if (d.tdsApplied() && (d.tdsPercent() == null || d.tdsPercent().signum() < 0 || d.tdsPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("TDS % must be between 0 and 100");
        }
        return new PmsPayout.Details(Text.clean(d.amc()), from, to, d.amount(), d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO,
                d.invoiceRaised(), d.receivedOn(), Text.clean(d.remarks()));
    }

    private BigDecimal gstPercent(LocalDate day) {
        return taxes.on(Product.PMS, day).gstPercent();
    }

    private static PayoutView view(PmsPayout p) {
        return new PayoutView(p.getId(), p.getAmc(), YearMonth.from(p.getPeriodFrom()), YearMonth.from(p.getPeriodTo()), p.getAmount(),
                p.isTdsApplied(), p.getTdsPercent(), p.getTds(), p.getGstPercent(), p.getGst(), p.getNet(), p.getInvoice(), p.isInvoiceRaised(),
                p.getReceivedOn(), p.getRemarks(), p.getStatus(), p.getUpdatedBy(), p.getUpdatedAt());
    }

    public record PayoutView(Long id, String amc, YearMonth periodFrom, YearMonth periodTo, BigDecimal amount, boolean tdsApplied,
                             BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst, BigDecimal net, BigDecimal invoice,
                             boolean invoiceRaised, LocalDate receivedOn, String remarks, PmsPayout.Status status, String updatedBy,
                             Instant updatedAt) {
    }
}
