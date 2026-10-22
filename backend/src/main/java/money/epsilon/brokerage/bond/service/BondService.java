package money.epsilon.brokerage.bond.service;

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
import money.epsilon.brokerage.bond.domain.BondEntry;
import money.epsilon.brokerage.bond.domain.BondEntry.Details;
import money.epsilon.brokerage.bond.repository.BondEntryRepository;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.intermediary.service.IntermediaryService;
import money.epsilon.brokerage.intermediary.service.IntermediaryService.TermsView;
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
 * Bond deals (one per transaction). Revenue is worked out on every save from the deal's mark-up, the platform's terms of
 * the deal's month (R-75) and the Bonds GST % (R-71). Region and RD come from the RM's place in the org that month.
 * Every change is audited.
 */
@Service
public class BondService {

    static final String AUDIT = "BOND_ENTRY";

    private final BondEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final IntermediaryService platforms;
    private final ProductTaxService taxes;
    private final AuditService audit;

    BondService(BondEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
                IntermediaryService platforms, ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.platforms = platforms;
        this.taxes = taxes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BondView> list(YearMonth month) {
        return views(entries.findByMonthOrderByInvestmentDateAscIdAsc(month.atDay(1)));
    }

    /**
     * Search across all months, newest first (R-77). Every part is optional: month range, text (client name, PAN, code,
     * bond name, ISIN), RM, referral / sub-broker, platform, payment status; cancelled entries only when asked for.
     */
    @Transactional(readOnly = true)
    public List<BondView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<BondEntry> found = entries.findByMonthBetweenOrderByMonthDescInvestmentDateDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == BondEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.platformId() == null || f.platformId().equals(e.getPlatformId()))
                .filter(e -> f.payment() == null || f.payment() == e.getPaymentStatus())
                .toList();
        List<BondView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.bondName(), q)
                || contains(v.isin(), q) || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    /** What the bond list can be narrowed by; every part optional. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, Long platformId, BondEntry.PaymentStatus payment,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public BondView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /** Bond invoices not paid yet, or paid without GST (the alert on Home). */
    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndPaymentStatusIn(BondEntry.Status.ACTIVE,
                EnumSet.of(BondEntry.PaymentStatus.PENDING, BondEntry.PaymentStatus.RECEIVED_GST_PENDING));
    }

    /** Bond names and ISINs used before, and the mark-up last used for each platform + ISIN (or bond name) — newest wins. */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        Map<String, String> isinOfName = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        Map<String, MarkupUsed> markups = new LinkedHashMap<>();
        for (BondEntry e : entries.findTop500ByStatusOrderByIdDesc(BondEntry.Status.ACTIVE)) {
            names.add(e.getBondName());
            if (e.getIsin() != null) isinOfName.putIfAbsent(Text.key(e.getBondName()), e.getIsin());
            markups.putIfAbsent(e.getPlatformId() + "|" + Text.key(e.getIsin() != null ? e.getIsin() : e.getBondName()),
                    new MarkupUsed(e.getPlatformId(), e.getBondName(), e.getIsin(), e.getMarkupPercent()));
        }
        return new Suggestions(names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                names.stream().filter(n -> isinOfName.containsKey(Text.key(n))).map(n -> new NameIsin(n, isinOfName.get(Text.key(n)))).toList(),
                List.copyOf(markups.values()));
    }

    public record MarkupUsed(long platformId, String bondName, String isin, BigDecimal markupPercent) {
    }

    public record NameIsin(String bondName, String isin) {
    }

    public record Suggestions(List<String> bondNames, List<NameIsin> isins, List<MarkupUsed> markups) {
    }

    /** The month's bond deals as an Excel sheet in the bond register's column order. */
    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return BondWorkbook.build(search(f));
    }

    /** The month's active deals as processing needs them (R-74): gross = invoice (incl. GST), gst, net = gross excl. GST. */
    @Transactional(readOnly = true)
    public List<BondForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByInvestmentDateAscIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == BondEntry.Status.ACTIVE)
                .map(e -> new BondForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record BondForLedger(long bondEntryId, long clientId, Long partnerId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** What the END file shows for each deal: bond name, ISIN, consideration. */
    @Transactional(readOnly = true)
    public Map<Long, BondLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, BondLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(), new BondLabel(e.getBondName(), e.getIsin(), e.getConsideration())));
        return result;
    }

    public record BondLabel(String bondName, String isin, BigDecimal consideration) {
    }

    /** A new deal: an existing client ({@code clientId}) or a new one (name + PAN), added with the deal's RM from its month. */
    @Transactional
    public BondView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "Bond entry");
        }
        BondEntry e = new BondEntry(client);
        e.fill(details, terms(details));
        entries.saveAndFlush(e);
        BondView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public BondView update(long id, Details d) {
        BondEntry e = find(id);
        if (e.getStatus() == BondEntry.Status.CANCELLED) throw new RuleViolationException("This bond entry is cancelled");
        Details details = check(d);
        BondView before = get(id);
        e.fill(details, terms(details));
        entries.saveAndFlush(e);
        BondView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        BondEntry e = find(id);
        if (e.getStatus() == BondEntry.Status.CANCELLED) return;
        BondView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Objects.requireNonNullElse(Text.clean(reason), "")));
    }

    private BondEntry.Terms terms(Details d) {
        TermsView t = platforms.on(d.platformId(), d.month());
        return new BondEntry.Terms(t.gstInclusive(), t.ourShare(), taxes.on(Product.BOND, d.month()).gstPercent());
    }

    private Details check(Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        platforms.get(d.platformId());
        if (d.bondName() == null || d.bondName().isBlank()) throw new IllegalArgumentException("Enter the bond name");
        if (d.investmentDate() == null) throw new IllegalArgumentException("Enter the investment date");
        if (d.maturityDate() != null && d.maturityDate().isBefore(d.investmentDate())) {
            throw new IllegalArgumentException("The maturity / sell date must be after the investment date");
        }
        if (d.quantity() == null || d.quantity().signum() <= 0) throw new IllegalArgumentException("Enter the quantity");
        if (d.investmentAmount() == null || d.investmentAmount().signum() <= 0) throw new IllegalArgumentException("Enter the investment amount");
        // Accrued interest may be negative: a deal between the record date and the coupon date pays the buyer back.
        requirePercent(d.markupPercent(), "Mark-up");
        if (d.tdsApplied()) requirePercent(d.tdsPercent(), "TDS");
        return new Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                d.platformId(), Text.clean(d.bondName()), Text.clean(d.isin()) == null ? null : Text.clean(d.isin()).toUpperCase(),
                d.couponPercent(), d.ytmPercent(), d.payoutFrequency(), Text.clean(d.paymentSchedule()), d.investmentDate(), d.allotmentDate(),
                d.maturityDate(), d.quantity(), d.ratePerUnit(), d.faceValue(), d.investmentAmount(), d.accruedInterest(), d.markupPercent(),
                d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, d.invoiceRaised(), d.invoiceDate(), d.paymentReceivedOn(),
                d.paymentStatus(), d.onPortal(), Text.clean(d.remarks()), d.given());
    }

    private static void requirePercent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException("Enter the " + what.toLowerCase() + " %");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
    }

    private BondEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("Bond entry " + id + " not found"));
    }

    /** The bond entries of these clients (cancelled ones left out), all months, newest first: the client page's holdings (R-09). */
    @Transactional(readOnly = true)
    public List<BondView> forClients(java.util.Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return List.of();
        return views(entries.findByClientIdInOrderByMonthDescIdDesc(clientIds).stream().filter(e -> e.getStatus() == BondEntry.Status.ACTIVE).toList());
    }

    private List<BondView> views(List<BondEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(BondEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(BondEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<Long, String> platformNames = new HashMap<>();
        list.stream().map(BondEntry::getPlatformId).distinct().forEach(id -> platformNames.put(id, platforms.get(id).name()));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            String code = codes.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            return new BondView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getPlatformId(), platformNames.get(e.getPlatformId()), e.getBondName(), e.getIsin(),
                    e.getCouponPercent(), e.getYtmPercent(), e.getPayoutFrequency(), e.getPaymentSchedule(), e.getInvestmentDate(),
                    e.getAllotmentDate(), e.getMaturityDate(), e.getQuantity(), e.getRatePerUnit(), e.getFaceValue(), e.getInvestmentAmount(),
                    e.getAccruedInterest(), e.getConsideration(), e.getMarkupPercent(), e.getOurShare(), e.isGstInclusive(), e.getBrokerage(),
                    e.getGross(), e.isTdsApplied(), e.getTdsPercent(), e.getTds(), e.getGstPercent(), e.getGst(), e.getNet(), e.getInvoice(),
                    e.isFiguresAsGiven(), e.isInvoiceRaised(), e.getInvoiceDate(), e.getPaymentReceivedOn(), e.getPaymentStatus(), e.isOnPortal(), e.getRemarks(),
                    e.getStatus(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
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

    private static Map<String, Object> auditView(BondView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("platform", v.platformName());
        m.put("bond", v.bondName() + (v.isin() == null ? "" : " " + v.isin()));
        m.put("investment", v.investmentAmount());
        m.put("consideration", v.consideration());
        m.put("markup", v.markupPercent());
        m.put("gross", v.gross());
        m.put("tds", v.tds());
        m.put("gst", v.gst());
        m.put("net", v.net());
        m.put("invoice", v.invoice());
        m.put("payment", v.paymentStatus());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record BondView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                           Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan,
                           String clientType, String mobile, String email, String address, Long platformId, String platformName,
                           String bondName, String isin, BigDecimal couponPercent, BigDecimal ytmPercent,
                           BondEntry.PayoutFrequency payoutFrequency, String paymentSchedule, LocalDate investmentDate,
                           LocalDate allotmentDate, LocalDate maturityDate, BigDecimal quantity, BigDecimal ratePerUnit,
                           BigDecimal faceValue, BigDecimal investmentAmount, BigDecimal accruedInterest, BigDecimal consideration,
                           BigDecimal markupPercent, BigDecimal ourShare, boolean gstInclusive, BigDecimal brokerage, BigDecimal gross,
                           boolean tdsApplied, BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst,
                           BigDecimal net, BigDecimal invoice, boolean figuresAsGiven, boolean invoiceRaised, LocalDate invoiceDate, LocalDate paymentReceivedOn,
                           BondEntry.PaymentStatus paymentStatus, boolean onPortal, String remarks, BondEntry.Status status,
                           String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
