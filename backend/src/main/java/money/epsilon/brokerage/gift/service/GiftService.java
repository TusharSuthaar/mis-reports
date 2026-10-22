package money.epsilon.brokerage.gift.service;

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
import money.epsilon.brokerage.gift.domain.GiftEntry;
import money.epsilon.brokerage.gift.domain.GiftEntry.Details;
import money.epsilon.brokerage.gift.repository.GiftEntryRepository;
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
 * GIFT City investments (R-82): one entry per investment, with its brokerage typed for now. GST % from Products &amp; taxes
 * for the month; TDS % the entry's own. Region and RD come from the RM's place in the org that month. Every change is audited.
 */
@Service
public class GiftService {

    static final String AUDIT = "GIFT_ENTRY";

    private final GiftEntryRepository entries;
    private final ClientService clients;
    private final EmployeeService employees;
    private final OrgChartService org;
    private final PartnerService partners;
    private final ProductTaxService taxes;
    private final AuditService audit;

    GiftService(GiftEntryRepository entries, ClientService clients, EmployeeService employees, OrgChartService org, PartnerService partners,
                ProductTaxService taxes, AuditService audit) {
        this.entries = entries;
        this.clients = clients;
        this.employees = employees;
        this.org = org;
        this.partners = partners;
        this.taxes = taxes;
        this.audit = audit;
    }

    /** Every part optional; cancelled entries only when asked for (R-77). {@code fund} narrows like a platform. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId, String fund, GiftEntry.PaymentStatus payment,
                         boolean includeCancelled) {
    }

    @Transactional(readOnly = true)
    public List<GiftView> search(Filter f) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        List<GiftEntry> found = entries.findByMonthBetweenOrderByMonthDescIdDesc(from, to).stream()
                .filter(e -> f.includeCancelled() || e.getStatus() == GiftEntry.Status.ACTIVE)
                .filter(e -> f.rmId() == null || f.rmId().equals(e.getRmId()))
                .filter(e -> f.partnerId() == null || f.partnerId().equals(e.getPartnerId()))
                .filter(e -> f.payment() == null || f.payment() == e.getPaymentStatus())
                .filter(e -> f.fund() == null || f.fund().isBlank() || Text.key(e.getFund()).equals(Text.key(f.fund())))
                .toList();
        List<GiftView> views = views(found);
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        if (q.isEmpty()) return views;
        return views.stream().filter(v -> contains(v.clientName(), q) || contains(v.clientType(), q) || contains(v.fund(), q)
                || (v.pan() != null && v.pan().equalsIgnoreCase(q))).toList();
    }

    private static boolean contains(String value, String upperQuery) {
        return value != null && value.toUpperCase().contains(upperQuery);
    }

    @Transactional(readOnly = true)
    public GiftView get(long id) {
        return views(List.of(find(id))).get(0);
    }

    /** The month's active GIFT City entries with brokerage, for processing (R-82): gross = invoice, gst, net = brokerage excl. GST. */
    @Transactional(readOnly = true)
    public List<GiftForLedger> forLedger(YearMonth month) {
        return entries.findByMonthOrderByIdAsc(month.atDay(1)).stream()
                .filter(e -> e.getStatus() == GiftEntry.Status.ACTIVE && e.getGross().signum() > 0)
                .map(e -> new GiftForLedger(e.getId(), e.getClientId(), e.getPartnerId(), e.getInvoice(), e.getGst(), e.getGross()))
                .toList();
    }

    public record GiftForLedger(long giftEntryId, long clientId, Long partnerId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** What the END file shows for each entry: the fund, and the rupee amount as AUM. */
    @Transactional(readOnly = true)
    public Map<Long, GiftLabel> labels(java.util.Collection<Long> ids) {
        Map<Long, GiftLabel> result = new HashMap<>();
        entries.findAllById(ids).forEach(e -> result.put(e.getId(), new GiftLabel(e.getFund(), e.getAmountInr())));
        return result;
    }

    public record GiftLabel(String fund, BigDecimal amountInr) {
    }

    @Transactional(readOnly = true)
    public long pendingPayments() {
        return entries.countByStatusAndPaymentStatusIn(GiftEntry.Status.ACTIVE, EnumSet.of(GiftEntry.PaymentStatus.PENDING));
    }

    /** Funds used before, for the form's suggestions. */
    @Transactional(readOnly = true)
    public List<String> funds() {
        LinkedHashSet<String> funds = new LinkedHashSet<>();
        entries.findTop500ByStatusOrderByIdDesc(GiftEntry.Status.ACTIVE).forEach(e -> funds.add(e.getFund()));
        return funds.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f) {
        return GiftWorkbook.build(search(f));
    }

    @Transactional
    public GiftView create(Long clientId, String newClientName, String newClientPan, Details d) {
        Details details = check(d);
        long client;
        if (clientId != null) {
            clients.requireExists(clientId);
            client = clientId;
        } else {
            if (newClientName == null || newClientName.isBlank()) throw new IllegalArgumentException("Pick the client or enter a new client's name");
            client = clients.addByHand(newClientName, newClientPan, details.rmId(), details.month(), "GIFT City entry");
        }
        GiftEntry e = new GiftEntry(client);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        GiftView view = get(e.getId());
        audit.record(AUDIT, e.getId(), "CREATE", null, auditView(view));
        return view;
    }

    @Transactional
    public GiftView update(long id, Details d) {
        GiftEntry e = find(id);
        if (e.getStatus() == GiftEntry.Status.CANCELLED) throw new RuleViolationException("This GIFT City entry is cancelled");
        Details details = check(d);
        GiftView before = get(id);
        e.fill(details, gstPercent(details.month()));
        entries.saveAndFlush(e);
        GiftView after = get(id);
        audit.record(AUDIT, id, "UPDATE", auditView(before), auditView(after));
        return after;
    }

    @Transactional
    public void cancel(long id, String reason) {
        GiftEntry e = find(id);
        if (e.getStatus() == GiftEntry.Status.CANCELLED) return;
        GiftView before = get(id);
        e.cancel();
        entries.saveAndFlush(e);
        audit.record(AUDIT, id, "CANCEL", auditView(before), Map.of("reason", Text.clean(reason) == null ? "" : Text.clean(reason)));
    }

    private Details check(Details d) {
        if (d.month() == null) throw new IllegalArgumentException("Choose the month");
        LocalDate month = d.month().withDayOfMonth(1);
        employees.requireEmployedOn(d.rmId(), month);
        if (d.partnerId() != null) partners.get(d.partnerId());
        if (Text.clean(d.fund()) == null) throw new IllegalArgumentException("Enter the fund / partner (e.g. DSP GIFT CITY)");
        if (d.amountUsd() == null && d.amountInr() == null) throw new IllegalArgumentException("Enter the amount in USD or in rupees");
        if (d.amountUsd() != null && d.amountUsd().signum() <= 0) throw new IllegalArgumentException("The USD amount must be more than 0");
        if (d.amountInr() != null && d.amountInr().signum() <= 0) throw new IllegalArgumentException("The rupee amount must be more than 0");
        if (d.fxRate() != null && d.fxRate().signum() <= 0) throw new IllegalArgumentException("The exchange rate must be more than 0");
        if (d.brokerage() != null && d.brokerage().signum() < 0) throw new IllegalArgumentException("The brokerage cannot be negative");
        if (d.tdsApplied() && (d.tdsPercent() == null || d.tdsPercent().signum() < 0 || d.tdsPercent().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("TDS % must be between 0 and 100");
        }
        if (d.paymentStatus() != GiftEntry.PaymentStatus.RECEIVED && d.receivedOn() != null) {
            throw new IllegalArgumentException("A received date needs the payment status Received");
        }
        return new Details(month, d.rmId(), d.partnerId(), Text.clean(d.mobile()), Text.clean(d.email()), Text.clean(d.address()),
                Text.clean(d.fund()), d.amountUsd(), d.fxRate(), d.fxRateDate(), Text.clean(d.fxSource()), d.amountInr(), d.onPortal(), d.trxnStatus(), d.brokerage(), d.tdsApplied(),
                d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO, d.invoiceRaised(), d.paymentStatus(), d.receivedOn(), Text.clean(d.remarks()));
    }

    private BigDecimal gstPercent(LocalDate month) {
        return taxes.on(Product.GIFT, month).gstPercent();
    }

    private GiftEntry find(long id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("GIFT City entry " + id + " not found"));
    }

    /** The GIFT City entries of these clients (cancelled ones left out), all months, newest first: the client page's holdings (R-09). */
    @Transactional(readOnly = true)
    public List<GiftView> forClients(java.util.Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return List.of();
        return views(entries.findByClientIdInOrderByMonthDescIdDesc(clientIds).stream().filter(e -> e.getStatus() == GiftEntry.Status.ACTIVE).toList());
    }

    private List<GiftView> views(List<GiftEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> clientIds = list.stream().map(GiftEntry::getClientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(clientIds);
        Map<Long, String> pans = clients.pans(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, PartnerView> partnerById = new HashMap<>();
        list.stream().map(GiftEntry::getPartnerId).filter(Objects::nonNull).distinct().forEach(id -> partnerById.put(id, partners.get(id)));
        Map<LocalDate, Map<Long, Place>> placesByMonth = new HashMap<>();
        Map<Long, String> codes = clients.codes(clientIds);
        return list.stream().map(e -> {
            Place place = placesByMonth.computeIfAbsent(e.getMonth(), m -> places(org.asOf(m))).get(e.getRmId());
            String pan = pans.get(e.getClientId());
            PartnerView p = e.getPartnerId() == null ? null : partnerById.get(e.getPartnerId());
            String code = codes.get(e.getClientId());
            return new GiftView(e.getId(), YearMonth.from(e.getMonth()), e.getRmId(), place == null ? employees.get(e.getRmId()).name() : place.rm(),
                    place == null ? null : place.branch(), place == null ? null : place.region(), place == null ? null : place.rd(),
                    e.getPartnerId(), p == null ? null : p.name(), p == null ? null : p.type().name(),
                    e.getClientId(), names.get(e.getClientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan), code == null ? "OFFLINE" : code,
                    e.getMobile(), e.getEmail(), e.getAddress(), e.getFund(), e.getAmountUsd(), e.getFxRate(), e.getFxRateDate(), e.getFxSource(), e.getAmountInr(), e.isOnPortal(),
                    e.getTrxnStatus(), e.getBrokerage(), e.getGross(), e.isTdsApplied(), e.getTdsPercent(), e.getTds(), e.getGstPercent(), e.getGst(),
                    e.getNet(), e.getInvoice(), e.isInvoiceRaised(), e.getPaymentStatus(), e.getReceivedOn(), e.getRemarks(), e.getStatus(),
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

    private static Map<String, Object> auditView(GiftView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", v.month());
        m.put("client", v.clientName());
        m.put("pan", v.pan() == null ? null : PanCipher.mask(v.pan()));
        m.put("rm", v.rmName());
        m.put("partner", v.partnerName());
        m.put("fund", v.fund());
        m.put("usd", v.amountUsd());
        m.put("rate", v.fxRate() == null ? null : v.fxRate() + (v.fxSource() == null ? "" : " (" + v.fxSource() + ")"));
        m.put("inr", v.amountInr());
        m.put("trxn", v.trxnStatus());
        m.put("brokerage", v.brokerage());
        m.put("tds", v.tds());
        m.put("gst", v.gst());
        m.put("payment", v.paymentStatus());
        return m;
    }

    private record Place(String rm, String branch, String region, String rd) {
    }

    public record GiftView(Long id, YearMonth month, Long rmId, String rmName, String branchName, String region, String rd,
                           Long partnerId, String partnerName, String partnerType, Long clientId, String clientName, String pan, String clientType,
                           String mobile, String email, String address, String fund, BigDecimal amountUsd, BigDecimal fxRate, LocalDate fxRateDate, String fxSource,
                           BigDecimal amountInr, boolean onPortal, GiftEntry.TrxnStatus trxnStatus, BigDecimal brokerage, BigDecimal gross, boolean tdsApplied,
                           BigDecimal tdsPercent, BigDecimal tds, BigDecimal gstPercent, BigDecimal gst, BigDecimal net, BigDecimal invoice,
                           boolean invoiceRaised, GiftEntry.PaymentStatus paymentStatus, LocalDate receivedOn, String remarks, GiftEntry.Status status,
                           String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }
}
