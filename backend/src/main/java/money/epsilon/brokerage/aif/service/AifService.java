package money.epsilon.brokerage.aif.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.aif.domain.AifEntry;
import money.epsilon.brokerage.aif.domain.AifEntry.Details;
import money.epsilon.brokerage.aif.domain.AifInstalment;
import money.epsilon.brokerage.aif.repository.AifEntryRepository;
import money.epsilon.brokerage.aif.repository.AifInstalmentRepository;
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
 * AIF commitments (one per commitment) with their drawdown instalments. Revenue is worked out on every save from the
 * commitment, the mark-up, the platform's terms of the month (R-75) and the AIF GST % (R-71) — see R-78. A deal may carry
 * its own revenue-sharing % for its referral / sub-broker. Every change is audited.
 */
@Service
public class AifService {

    static final String AUDIT = "AIF_ENTRY";

    private final AifEntryRepository entries;
    private final AifInstalmentRepository instalments;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final IntermediaryService platforms;
    private final ProductTaxService taxes;
    private final AuditService audit;

    AifService(AifEntryRepository entries, AifInstalmentRepository instalments, ClientService clients, EmployeeService employees,
               OrgChartService org, PartnerService partners, IntermediaryService platforms, ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.instalments = instalments;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.platforms = platforms;
        this.taxes = taxes;
        this.audit = audit;
    }

    /** Search across all months, newest first (R-77); every part optional. Text: client name, PAN, code, fund name. */
    @Transactional(readOnly = true)
    public List<AifView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<AifEntry> found = entries.findByMonthBetweenOrderByMonthDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == AifEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.platformId() == null || f.platformId().equals(e.getPlatformId()))
                .filter(e -> f.payment() == null || f.payment() == e.getPaymentStatus())
                .toList();
        List<AifView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.fundName(), q)
                || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, Long platformId, AifEntry.PaymentStatus payment,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public AifView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndPaymentStatusIn(AifEntry.Status.ACTIVE,
                EnumSet.of(AifEntry.PaymentStatus.PENDING, AifEntry.PaymentStatus.RECEIVED_GST_PENDING));
    }

    /** Fund names used before, and the mark-up last used for each platform + fund (newest wins). */
    @Transactional(readOnly = true)
    public Suggestions suggestions() {
        Set<String> names = new LinkedHashSet<>();
        Map<String, MarkupUsed> markups = new LinkedHashMap<>();
        for (AifEntry e : entries.findTop500ByStatusOrderByIdDesc(AifEntry.Status.ACTIVE)) {
            names.add(e.getFundName());
            markups.putIfAbsent(e.getPlatformId() + "|" + Text.key(e.getFundName()), new MarkupUsed(e.getPlatformId(), e.getFundName(), e.getMarkupPercent()));
        }
        return new Suggestions(names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), List.copyOf(markups.values()));
    }

    public record MarkupUsed(long platformId, String fundName, BigDecimal markupPercent) {
    }

    public record Suggestions(List<String> fundNames, List<MarkupUsed> markups) {
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return AifWorkbook.build(search(f));
    }

    /**
     * The month's active commitments as processing needs them (R-74): gross = invoice (incl. GST), gst, net = gross excl. GST,
     * and the deal's own sharing % for its partner (null = the partner's AIF %).
     */
    @Transactional(readOnly = true)
    public List<AifForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == AifEntry.Status.ACTIVE)
                .map(e -> new AifForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getSharingPercent(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record AifForLedger(long aifEntryId, long clientId, Long partnerId, BigDecimal sharingPercent, BigDecimal gross, BigDecimal gst,
                               BigDecimal net) {
    }

    /** What the END file shows for each commitment: fund name and commitment. */
    @Transactional(readOnly = true)
    public Map<Long, AifLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, AifLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(), new AifLabel(e.getFundName(), e.getCommitment())));
        return result;
    }

    public record AifLabel(String fundName, BigDecimal commitment) {
    }

    @Transactional
    public AifView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "AIF entry");
        }
        AifEntry e = new AifEntry(client);
        e.fill(details, terms(details));
        entries.saveAndFlush(e);
        saveInstalments(e.getId(), details.instalments());
        AifView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public AifView update(long id, Details d) {
        AifEntry e = find(id);
        if (e.getStatus() == AifEntry.Status.CANCELLED) throw new RuleViolationException("This AIF entry is cancelled");
        Details details = check(d);
        AifView before = get(id);
        e.fill(details, terms(details));
        entries.saveAndFlush(e);
        instalments.deleteByAifEntryId(id);
        instalments.flush();
        saveInstalments(id, details.instalments());
        AifView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        AifEntry e = find(id);
        if (e.getStatus() == AifEntry.Status.CANCELLED) return;
        AifView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Objects.requireNonNullElse(Text.clean(reason), "")));
    }

    private void saveInstalments(long entryId, List<AifEntry.Instalment> list) {
        int seq = 1;
        for (AifEntry.Instalment i : list) instalments.save(new AifInstalment(entryId, seq++, i.amount(), i.paidOn()));
        instalments.flush();
    }

    private AifEntry.Terms terms(Details d) {
        TermsView t = platforms.on(d.platformId(), d.month());
        return new AifEntry.Terms(t.gstInclusive(), t.ourShare(), taxes.on(Product.AIF, d.month()).gstPercent());
    }

    private Details check(Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        platforms.get(d.platformId());
        if (d.fundName() == null || d.fundName().isBlank()) throw new IllegalArgumentException("Enter the AIF (fund) name");
        if (d.commitment() == null || d.commitment().signum() <= 0) throw new IllegalArgumentException("Enter the commitment amount");
        requirePercent(d.markupPercent(), "Mark-up");
        if (d.tdsApplied()) requirePercent(d.tdsPercent(), "TDS");
        if (d.sharingPercent() != null) requirePercent(d.sharingPercent(), "Revenue sharing");
        List<AifEntry.Instalment> list = new ArrayList<>();
        BigDecimal called = BigDecimal.ZERO;
        for (AifEntry.Instalment i : d.instalments() == null ? List.<AifEntry.Instalment>of() : d.instalments()) {
            if (i == null || i.amount() == null) continue;
            if (i.amount().signum() <= 0) throw new IllegalArgumentException("An instalment amount must be above 0");
            list.add(i);
            called = called.add(i.amount());
        }
        if (called.compareTo(d.commitment()) > 0) throw new IllegalArgumentException("The instalments add up to more than the commitment");
        return new Details(month, d.rmId(), d.partnerId(), d.sharingPercent(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                d.platformId(), Text.clean(d.fundName()), d.commitment(), d.agreementDate(), Text.clean(d.tenure()), d.maturityDate(),
                d.soaReceived(), d.applicationForm(), d.investmentMail(), d.quantity(), d.price(), d.certificate(), Text.clean(d.payoutTerms()),
                d.markupPercent(), d.tdsApplied(), d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, d.invoiceRaised(), d.invoiceDate(),
                d.paymentReceivedOn(), d.paymentStatus(), d.partnerPaid(), d.onInvestwell(), Text.clean(d.remarks()), list, d.given());
    }

    private static void requirePercent(BigDecimal value, String what) {
        if (value == null) throw new IllegalArgumentException("Enter the " + what.toLowerCase() + " %");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException(what + " % must be between 0 and 100");
    }

    private AifEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("AIF entry " + id + " not found"));
    }

    private List<AifView> views(List<AifEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(AifEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(AifEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<Long, String> platformNames = new HashMap<>();
        list.stream().map(AifEntry::getPlatformId).distinct().forEach(id -> platformNames.put(id, platforms.get(id).name()));
        Map<Long, List<InstalmentView>> drawdowns = new HashMap<>();
        instalments.findByAifEntryIdInOrderByAifEntryIdAscSeqAsc(list.stream().map(AifEntry::getId).toList())
                .forEach(i -> drawdowns.computeIfAbsent(i.getAifEntryId(), k -> new ArrayList<>()).add(new InstalmentView(i.getSeq(), i.getAmount(), i.getPaidOn())));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            String code = codes.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            List<InstalmentView> paid = drawdowns.getOrDefault(e.getId(), List.of());
            BigDecimal called = paid.stream().map(InstalmentView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AifView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(), e.getSharingPercent(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getPlatformId(), platformNames.get(e.getPlatformId()), e.getFundName(),
                    e.getCommitment(), paid, called, e.getAgreementDate(), e.getTenure(), e.getMaturityDate(), e.isSoaReceived(),
                    e.isApplicationForm(), e.isInvestmentMail(), e.getQuantity(), e.getPrice(), e.getCertificate(), e.getPayoutTerms(),
                    e.getMarkupPercent(), e.getOurShare(), e.isGstInclusive(), e.getBrokerage(), e.getGross(), e.isTdsApplied(), e.getTdsPercent(),
                    e.getTds(), e.getGstPercent(), e.getGst(), e.getNet(), e.getInvoice(), e.isFiguresAsGiven(), e.isInvoiceRaised(), e.getInvoiceDate(),
                    e.getPaymentReceivedOn(), e.getPaymentStatus(), e.isPartnerPaid(), e.isOnInvestwell(), e.getRemarks(), e.getStatus(),
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

    private static Map<String, Object> auditView(AifView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("sharing", v.sharingPercent());
        m.put("platform", v.platformName());
        m.put("fund", v.fundName());
        m.put("commitment", v.commitment());
        m.put("called", v.called());
        m.put("markup", v.markupPercent());
        m.put("gross", v.gross());
        m.put("net", v.net());
        m.put("invoice", v.invoice());
        m.put("payment", v.paymentStatus());
        m.put("partnerPaid", v.partnerPaid());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record InstalmentView(int seq, BigDecimal amount, LocalDate paidOn) {
    }

    public record AifView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                          Long partnerId, String partnerName, String partnerType, BigDecimal sharingPercent, Long clientId, String clientName,
                          String pan, String clientType, String mobile, String email, String address, Long platformId, String platformName,
                          String fundName, BigDecimal commitment, List<InstalmentView> instalments, BigDecimal called,
                          LocalDate agreementDate, String tenure, LocalDate maturityDate, boolean soaReceived, boolean applicationForm,
                          boolean investmentMail, BigDecimal quantity, BigDecimal price, AifEntry.Certificate certificate, String payoutTerms,
                          BigDecimal markupPercent, BigDecimal ourShare, boolean gstInclusive, BigDecimal brokerage, BigDecimal gross,
                          boolean tdsApplied, BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst, BigDecimal net,
                          BigDecimal invoice, boolean figuresAsGiven, boolean invoiceRaised, LocalDate invoiceDate, LocalDate paymentReceivedOn,
                          AifEntry.PaymentStatus paymentStatus, boolean partnerPaid, boolean onInvestwell, String remarks,
                          AifEntry.Status status, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
