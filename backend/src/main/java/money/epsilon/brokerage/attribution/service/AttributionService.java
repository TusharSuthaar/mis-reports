package money.epsilon.brokerage.attribution.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.attribution.service.SplitCalculator.NegativeNet;
import money.epsilon.brokerage.attribution.service.SplitCalculator.Part;
import money.epsilon.brokerage.attribution.service.SplitCalculator.Share;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.aif.service.AifService;
import money.epsilon.brokerage.aif.service.AifService.AifForLedger;
import money.epsilon.brokerage.bond.service.BondService;
import money.epsilon.brokerage.bond.service.BondService.BondForLedger;
import money.epsilon.brokerage.fd.service.FdService;
import money.epsilon.brokerage.fd.service.FdService.FdForLedger;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LedgerService.ActiveLine;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.sharing.service.ShareService;
import money.epsilon.brokerage.sharing.service.ShareService.ShareView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The split: for every saved brokerage line of a month, who gets what. Everything is taken as it stood on the LAST DAY
 * of the month (rule R1): the client's RM and that RM's branch, region and RD, and the client's partner shares. A
 * correction to a locked month is split as that month stood. The names are stored with each split so later changes
 * never alter history. Re-running an open month voids the previous run.
 */
@Service
public class AttributionService {

    static final String MF = "MF";
    static final String FD = "FD";
    static final String BOND = "BOND";
    static final String AIF = "AIF";
    static final String LOAN = "LOAN";
    static final String GIFT = "GIFT";
    static final String EQUITY = "EQUITY";
    static final String PREIPO = "PREIPO";
    static final String PMS = "PMS";
    static final String NPS = "NPS";
    static final String P2P = "P2P";
    static final String INS = "INS";

    private final PeriodService periods;
    private final LedgerService ledger;
    private final ClientService clients;
    private final OrgChartService org;
    private final EmployeeService employees;
    private final ShareService shares;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;
    private final List<PartnerType> order;
    private final NegativeNet negativeNet;
    private final FdService fds;
    private final BondService bonds;
    private final AifService aifs;
    private final money.epsilon.brokerage.loan.service.LoanService loans;
    private final money.epsilon.brokerage.gift.service.GiftService gifts;
    private final money.epsilon.brokerage.equity.service.EquityService equity;
    private final money.epsilon.brokerage.preipo.service.PreIpoService preIpos;
    private final money.epsilon.brokerage.pms.service.PmsFeeService pmsFees;
    private final money.epsilon.brokerage.nps.service.NpsService npses;
    private final money.epsilon.brokerage.p2p.service.P2pService p2ps;
    private final money.epsilon.brokerage.insurance.service.InsuranceService insurance;

    AttributionService(PeriodService periods, LedgerService ledger, ClientService clients, OrgChartService org, EmployeeService employees,
                       ShareService shares, FdService fds, BondService bonds, AifService aifs,
                       money.epsilon.brokerage.loan.service.LoanService loans, money.epsilon.brokerage.gift.service.GiftService gifts,
                       money.epsilon.brokerage.equity.service.EquityService equity,
                       money.epsilon.brokerage.preipo.service.PreIpoService preIpos,
                       money.epsilon.brokerage.pms.service.PmsFeeService pmsFees, money.epsilon.brokerage.nps.service.NpsService npses,
                       money.epsilon.brokerage.p2p.service.P2pService p2ps,
                       money.epsilon.brokerage.insurance.service.InsuranceService insurance,
                       AuditService audit, JdbcClient jdbc, JdbcTemplate jdbcTemplate, ObjectMapper mapper,
                       @Value("${mis.sharing.order}") String order, @Value("${mis.sharing.negative-net:SAME_PERCENT}") NegativeNet negativeNet) {
        this.periods = periods;
        this.ledger = ledger;
        this.clients = clients;
        this.org = org;
        this.employees = employees;
        this.shares = shares;
        this.fds = fds;
        this.bonds = bonds;
        this.aifs = aifs;
        this.loans = loans;
        this.gifts = gifts;
        this.equity = equity;
        this.preIpos = preIpos;
        this.pmsFees = pmsFees;
        this.npses = npses;
        this.p2ps = p2ps;
        this.insurance = insurance;
        this.audit = audit;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.order = Arrays.stream(order.split(",")).map(String::trim).map(PartnerType::valueOf).toList();
        this.negativeNet = negativeNet;
    }

    public record RunResult(long runId, YearMonth month, int lines, BigDecimal net, BigDecimal partners, BigDecimal epsilon) {
    }

    /** Splits every active line of the month and marks it PROCESSED. */
    @Transactional
    public RunResult process(long periodId) {
        PeriodView period = periods.get(periodId);
        if (period.status() == Period.Status.LOCKED) throw new RuleViolationException(period.month() + " is locked");
        List<ActiveLine> mfLines = ledger.activeLines(periodId, MF);
        if (mfLines.isEmpty()) throw new RuleViolationException("Nothing to split: save " + period.month() + " first");
        // The month's FD entries become its FD lines, split with the partner chosen on each FD at its FD % (R-74).
        List<FdForLedger> fdEntries = fds.forLedger(period.month());
        ledger.replaceFdLines(periodId, fdEntries.stream()
                .map(f -> new LedgerService.FdLineInput(f.fdEntryId(), f.clientId(), f.gross(), f.gst(), f.net())).toList());
        Map<Long, Long> fdPartner = new HashMap<>();
        fdEntries.forEach(f -> { if (f.partnerId() != null) fdPartner.put(f.fdEntryId(), f.partnerId()); });
        List<BondForLedger> bondEntries = bonds.forLedger(period.month());
        ledger.replaceBondLines(periodId, bondEntries.stream()
                .map(b -> new LedgerService.BondLineInput(b.bondEntryId(), b.clientId(), b.gross(), b.gst(), b.net())).toList());
        Map<Long, Long> bondPartner = new HashMap<>();
        bondEntries.forEach(b -> { if (b.partnerId() != null) bondPartner.put(b.bondEntryId(), b.partnerId()); });
        List<ActiveLine> lines = new ArrayList<>(mfLines);
        lines.addAll(ledger.activeLines(periodId, FD));
        lines.addAll(ledger.activeLines(periodId, BOND));
        // AIF commitments (R-78): a deal's own sharing % for its partner wins over the partner's AIF %.
        List<AifForLedger> aifEntries = aifs.forLedger(period.month());
        ledger.replaceAifLines(periodId, aifEntries.stream()
                .map(a -> new LedgerService.AifLineInput(a.aifEntryId(), a.clientId(), a.gross(), a.gst(), a.net())).toList());
        Map<Long, AifForLedger> aifById = new HashMap<>();
        aifEntries.forEach(a -> aifById.put(a.aifEntryId(), a));
        lines.addAll(ledger.activeLines(periodId, AIF));
        // Loan payouts (R-80): split with the partner chosen on each entry at the partner's Loans %.
        var loanEntries = loans.forLedger(period.month());
        ledger.replaceLoanLines(periodId, loanEntries.stream()
                .map(x -> new LedgerService.LoanLineInput(x.loanEntryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
        Map<Long, Long> loanPartner = new HashMap<>();
        loanEntries.forEach(x -> { if (x.partnerId() != null) loanPartner.put(x.loanEntryId(), x.partnerId()); });
        lines.addAll(ledger.activeLines(periodId, LOAN));
        // GIFT City entries with brokerage (R-82): split with the partner chosen on each at the partner's GIFT %.
        var giftEntries = gifts.forLedger(period.month());
        ledger.replaceGiftLines(periodId, giftEntries.stream()
                .map(x -> new LedgerService.GiftLineInput(x.giftEntryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
        Map<Long, Long> giftPartner = new HashMap<>();
        giftEntries.forEach(x -> { if (x.partnerId() != null) giftPartner.put(x.giftEntryId(), x.partnerId()); });
        lines.addAll(ledger.activeLines(periodId, GIFT));
        // Equity brokerage (R-89): each client's franchise share of the month, split with the client's own partner % like MF.
        ledger.replaceEquityLines(periodId, equity.forLedger(period.month()).stream()
                .map(x -> new LedgerService.EquityLineInput(x.uploadId(), x.clientId(), x.share())).toList());
        List<ActiveLine> equityLines = ledger.activeLines(periodId, EQUITY);
        java.util.Set<Long> equityLineIds = new java.util.HashSet<>();
        equityLines.forEach(x -> equityLineIds.add(x.id()));
        lines.addAll(equityLines);
        // Pre-IPO deals (R-93): like AIF, a deal's own sharing % for its partner wins over the partner's Pre-IPO %.
        var preIpoEntries = preIpos.forLedger(period.month());
        ledger.replacePreIpoLines(periodId, preIpoEntries.stream()
                .map(x -> new LedgerService.PreIpoLineInput(x.preIpoEntryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
        Map<Long, money.epsilon.brokerage.preipo.service.PreIpoService.PreIpoForLedger> preIpoById = new HashMap<>();
        preIpoEntries.forEach(x -> preIpoById.put(x.preIpoEntryId(), x));
        lines.addAll(ledger.activeLines(periodId, PREIPO));
        // PMS revenue typed per client, NPS revenue and P2P brokerage (R-85..R-87): split with the partner on the investment /
        // entry at the partner's PMS / NPS / P2P %.
        var pmsEntries = pmsFees.forLedger(period.month());
        ledger.replacePmsLines(periodId, pmsEntries.stream()
                .map(x -> new LedgerService.OtherLineInput(x.pmsFeeId(), x.clientId(), x.amount(), java.math.BigDecimal.ZERO, x.amount())).toList());
        Map<Long, Long> pmsPartner = new HashMap<>();
        pmsEntries.forEach(x -> { if (x.partnerId() != null) pmsPartner.put(x.pmsFeeId(), x.partnerId()); });
        lines.addAll(ledger.activeLines(periodId, PMS));
        var npsEntries = npses.forLedger(period.month());
        ledger.replaceNpsLines(periodId, npsEntries.stream()
                .map(x -> new LedgerService.OtherLineInput(x.npsEntryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
        Map<Long, Long> npsPartner = new HashMap<>();
        npsEntries.forEach(x -> { if (x.partnerId() != null) npsPartner.put(x.npsEntryId(), x.partnerId()); });
        lines.addAll(ledger.activeLines(periodId, NPS));
        var p2pEntries = p2ps.forLedger(period.month());
        ledger.replaceP2pLines(periodId, p2pEntries.stream()
                .map(x -> new LedgerService.OtherLineInput(x.p2pEntryId(), x.clientId(), x.gross(), x.gst(), x.net())).toList());
        Map<Long, Long> p2pPartner = new HashMap<>();
        p2pEntries.forEach(x -> { if (x.partnerId() != null) p2pPartner.put(x.p2pEntryId(), x.partnerId()); });
        lines.addAll(ledger.activeLines(periodId, P2P));
        // Insurance policies (R-95): the expected commission, shared only with the policy's POSP, at the POSP's commission.
        var insEntries = insurance.forLedger(period.month());
        ledger.replaceInsuranceLines(periodId, insEntries.stream()
                .map(x -> new LedgerService.OtherLineInput(x.policyId(), x.clientId(), x.expectedComm(), java.math.BigDecimal.ZERO, x.expectedComm())).toList());
        Map<Long, money.epsilon.brokerage.insurance.service.InsuranceService.InsuranceForLedger> insById = new HashMap<>();
        insEntries.forEach(x -> insById.put(x.policyId(), x));
        lines.addAll(ledger.activeLines(periodId, INS));
        List<Long> own = lines.stream().filter(l -> l.adjustsPeriodId() == null).map(ActiveLine::clientId).distinct().toList();
        long notInMaster = own.size() - clients.rmsOn(own, period.toDate()).size();
        if (notInMaster > 0) {
            throw new RuleViolationException(notInMaster + " client(s) in " + period.month() + " are not in Master MIS (no RM). Add them to"
                    + " Master MIS, or give them an RM on the Clients screen, then process.");
        }
        voidSplits(periodId);

        // Each line is split as of the last day of the month it belongs to.
        Map<LocalDate, List<ActiveLine>> byDay = new LinkedHashMap<>();
        for (ActiveLine l : lines) {
            LocalDate day = l.adjustsPeriodId() == null ? period.toDate() : periods.get(l.adjustsPeriodId()).toDate();
            byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(l);
        }

        Instant started = Instant.now();
        List<Object[]> rows = new ArrayList<>();
        List<List<Object>> rulesUsed = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO, partners = BigDecimal.ZERO, epsilon = BigDecimal.ZERO;
        for (var e : byDay.entrySet()) {
            LocalDate day = e.getKey();
            List<Long> ids = e.getValue().stream().map(ActiveLine::clientId).distinct().toList();
            Map<Long, Long> rmOf = clients.rmsOn(ids, day);
            Map<Long, List<ShareView>> sharesOf = shares.on(ids, day);
            // A partner linked to clients but with no MF % would silently get 0: a gap in the master, so stop (R-54).
            java.util.Set<String> noPercent = new java.util.TreeSet<>();
            sharesOf.values().forEach(list -> list.stream().filter(s -> s.source() == ShareService.Source.NO_PARTNER_PERCENT)
                    .forEach(s -> noPercent.add(s.partnerName())));
            if (!noPercent.isEmpty()) {
                throw new RuleViolationException(noPercent.size() + " partner(s) have clients in " + period.month() + " but no MF % on " + day
                        + ": " + String.join(", ", noPercent) + ". Set their MF % on the Partners screen (or upload Master MIS with its"
                        + " partner sheets), then process.");
            }
            Map<Long, Placement> placementOf = placements(org.asOf(day));
            Map<Long, String> names = employees.list().stream().collect(java.util.stream.Collectors.toMap(EmployeeView::id, EmployeeView::name));
            java.util.Set<String> noFdPercent = new java.util.TreeSet<>();
            for (ActiveLine l : e.getValue()) {
                List<Share> lineShares;
                if (l.insurancePolicyId() != null) {
                    var x = insById.get(l.insurancePolicyId());
                    lineShares = x == null || x.pospId() == null || x.pospComm().signum() == 0 ? List.of()
                            : List.of(new Share(PartnerType.POSP, x.pospId(),
                                    x.pospName(),
                                    x.pospComm().divide(x.expectedComm(), 12, java.math.RoundingMode.HALF_UP)));
                } else if (l.pmsFeeId() != null || l.npsEntryId() != null || l.p2pEntryId() != null) {
                    Long partnerId = l.pmsFeeId() != null ? pmsPartner.get(l.pmsFeeId())
                            : l.npsEntryId() != null ? npsPartner.get(l.npsEntryId()) : p2pPartner.get(l.p2pEntryId());
                    Product product = l.pmsFeeId() != null ? Product.PMS : l.npsEntryId() != null ? Product.NPS : Product.P2P;
                    ShareService.ProductShare ps = partnerId == null ? null : shares.productShare(partnerId, product, day);
                    if (ps != null && ps.fraction() == null) noFdPercent.add(ps.partnerName() + " (" + PRODUCT_LABEL.get(product) + ")");
                    lineShares = ps == null || ps.fraction() == null ? List.of()
                            : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), ps.fraction()));
                } else if (l.preIpoEntryId() != null) {
                    var x = preIpoById.get(l.preIpoEntryId());
                    ShareService.ProductShare ps = x == null || x.partnerId() == null ? null : shares.productShare(x.partnerId(), Product.PREIPO, day);
                    java.math.BigDecimal fraction = ps == null ? null
                            : x.sharingPercent() != null ? x.sharingPercent().movePointLeft(2) : ps.fraction();
                    if (ps != null && fraction == null) noFdPercent.add(ps.partnerName() + " (Pre-IPO)");
                    lineShares = fraction == null ? List.of() : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), fraction));
                } else if (l.aifEntryId() != null) {
                    AifForLedger a = aifById.get(l.aifEntryId());
                    ShareService.ProductShare ps = a == null || a.partnerId() == null ? null : shares.productShare(a.partnerId(), Product.AIF, day);
                    java.math.BigDecimal fraction = ps == null ? null
                            : a.sharingPercent() != null ? a.sharingPercent().movePointLeft(2) : ps.fraction();
                    if (ps != null && fraction == null) noFdPercent.add(ps.partnerName() + " (AIF)");
                    lineShares = fraction == null ? List.of() : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), fraction));
                } else if (l.giftEntryId() != null) {
                    Long partnerId = giftPartner.get(l.giftEntryId());
                    ShareService.ProductShare ps = partnerId == null ? null : shares.productShare(partnerId, Product.GIFT, day);
                    if (ps != null && ps.fraction() == null) noFdPercent.add(ps.partnerName() + " (GIFT City)");
                    lineShares = ps == null || ps.fraction() == null ? List.of()
                            : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), ps.fraction()));
                } else if (l.loanEntryId() != null) {
                    Long partnerId = loanPartner.get(l.loanEntryId());
                    ShareService.ProductShare ps = partnerId == null ? null : shares.productShare(partnerId, Product.LOAN, day);
                    if (ps != null && ps.fraction() == null) noFdPercent.add(ps.partnerName() + " (Loans)");
                    lineShares = ps == null || ps.fraction() == null ? List.of()
                            : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), ps.fraction()));
                } else if (l.fdEntryId() != null || l.bondEntryId() != null) {
                    boolean fd = l.fdEntryId() != null;
                    Long partnerId = fd ? fdPartner.get(l.fdEntryId()) : bondPartner.get(l.bondEntryId());
                    ShareService.ProductShare ps = partnerId == null ? null : shares.productShare(partnerId, fd ? Product.FD : Product.BOND, day);
                    if (ps != null && ps.fraction() == null) noFdPercent.add(ps.partnerName() + (fd ? " (FD)" : " (Bonds)"));
                    lineShares = ps == null || ps.fraction() == null ? List.of()
                            : List.of(new Share(ps.role(), ps.partnerId(), ps.partnerName(), ps.fraction()));
                } else if (equityLineIds.contains(l.id())) {
                    // Equity (R-89): the client's partners, each at its own Equity % from the rate card (its MF % for this client
                    // when it has no Equity %), so a partner at 0% for MF can still share equity.
                    Map<Long, ShareService.ProductShare> equityCard = new HashMap<>();
                    lineShares = sharesOf.getOrDefault(l.clientId(), List.of()).stream().map(sv -> {
                        ShareService.ProductShare eq = equityCard.computeIfAbsent(sv.partnerId(), id -> shares.productShare(id, Product.EQUITY, day));
                        java.math.BigDecimal f = eq.fraction() != null ? eq.fraction() : sv.percent().movePointLeft(2);
                        return new Share(sv.partnerRole(), sv.partnerId(), sv.partnerName(), f);
                    }).toList();
                } else {
                    lineShares = sharesOf.getOrDefault(l.clientId(), List.of()).stream()
                            .map(s -> new Share(s.partnerRole(), s.partnerId(), s.partnerName(), s.percent().movePointLeft(2)))
                            .toList();
                }
                lineShares.forEach(s -> rulesUsed.add(List.of(l.clientId(), s.role().name(), s.partnerId(), s.fraction().toPlainString(), day.toString())));
                Long rmId = rmOf.get(l.clientId());
                if (rmId == null) {
                    throw new RuleViolationException("A client in " + period.month() + " has no RM on " + day + "; give every client an RM first");
                }
                Placement where = placementOf.getOrDefault(rmId, new Placement(names.get(rmId), null, null, null));
                for (Part part : SplitCalculator.split(l.net(), lineShares, order, negativeNet)) {
                    Share s = part.share();
                    rows.add(new Object[]{periodId, l.id(), l.clientId(), part.isCompany() ? "COMPANY" : "PARTNER",
                            s == null ? null : s.role().name(), s == null ? null : s.partnerId(), s == null ? null : s.partnerName(),
                            s == null ? null : s.fraction(), part.base(), part.amount(), rmId, where.rmName(), where.branch(), where.region(),
                            where.rd(), l.adjustsPeriodId() != null});
                    if (part.isCompany()) epsilon = epsilon.add(part.amount());
                    else partners = partners.add(part.amount());
                }
                net = net.add(l.net());
            }
            if (!noFdPercent.isEmpty()) {
                throw new RuleViolationException(noFdPercent.size() + " partner(s) on FD / bond / other product entries of " + period.month() + " have no FD % / Bonds % / AIF % / Loans % / GIFT % / Pre-IPO % on "
                        + day + ": " + String.join(", ", noFdPercent) + ". Set it on the Partners screen, then process.");
            }
        }

        String rulesJson = json(Map.of("order", order, "negativeNet", negativeNet, "shares", rulesUsed));
        long runId = jdbc.sql("""
                        INSERT INTO attribution_run (period_id, product_type, status, rules_json, rules_hash, lines, started_by, started_at, finished_at)
                        VALUES (?, ?, 'DONE', ?::jsonb, ?, ?, ?, ?, now()) RETURNING id""")
                .params(periodId, MF, rulesJson, sha256(rulesJson), lines.size(), CurrentUser.username(), Timestamp.from(started))
                .query(Long.class).single();
        jdbcTemplate.batchUpdate("""
                INSERT INTO brokerage_split (run_id, period_id, brokerage_line_id, client_id, payee_type, partner_role, partner_id, partner_name,
                                             share_pct, base, amount, rm_id_at_period, rm_at_period, branch_at_period, region_at_period,
                                             rd_at_period, is_adjustment)
                VALUES (""" + runId + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);

        // Every line must split back to exactly its net (the parts were built above from the ledger's own lines).
        Map<Long, BigDecimal> splitTotal = new java.util.HashMap<>();
        for (Object[] r : rows) splitTotal.merge((Long) r[1], (BigDecimal) r[9], BigDecimal::add);
        long unbalanced = lines.stream().filter(l -> splitTotal.getOrDefault(l.id(), BigDecimal.ZERO).compareTo(l.net()) != 0).count();
        if (unbalanced != 0) throw new IllegalStateException(unbalanced + " lines do not split back to their net; nothing was saved");

        periods.markProcessed(periodId, CurrentUser.username());
        RunResult result = new RunResult(runId, period.month(), lines.size(), net, partners, epsilon);
        audit.record("PERIOD", periodId, "PROCESS", null, result);
        return result;
    }

    /**
     * What stands between a month and processing it, checked without changing anything (R-91): the same checks
     * {@link #process} stops on, on the month's own figures — clients with no RM, partners with no % for a product they
     * have business in, equity PANs not in Clients — so they can be fixed before anyone presses Process.
     */
    public record Readiness(long periodId, YearMonth month, String status, boolean mfSaved, int mfClients, boolean equityUploaded,
                            int equityClients, int equityNotInClients, Map<String, Integer> entries, long notInMaster,
                            List<String> missingPercent, boolean ready) {
    }

    private static final Map<Product, String> PRODUCT_LABEL = Map.of(Product.FD, "FD", Product.BOND, "Bonds", Product.AIF, "AIF",
            Product.LOAN, "Loans", Product.GIFT, "GIFT City", Product.PREIPO, "Pre-IPO", Product.PMS, "PMS", Product.NPS, "NPS",
            Product.P2P, "P2P");

    @Transactional(readOnly = true)
    public Readiness readiness(long periodId) {
        PeriodView period = periods.get(periodId);
        YearMonth month = period.month();
        LocalDate day = period.toDate();
        List<Long> mfClients = ledger.activeLines(periodId, MF).stream().filter(l -> l.adjustsPeriodId() == null).map(ActiveLine::clientId)
                .distinct().toList();
        var eq = equity.check(month);
        List<FdForLedger> fdEntries = fds.forLedger(month);
        List<BondForLedger> bondEntries = bonds.forLedger(month);
        List<AifForLedger> aifEntries = aifs.forLedger(month);
        var loanEntries = loans.forLedger(month);
        var giftEntries = gifts.forLedger(month);
        var preIpoEntries = preIpos.forLedger(month);
        var pmsEntries = pmsFees.forLedger(month);
        var npsEntries = npses.forLedger(month);
        var p2pEntries = p2ps.forLedger(month);
        var insEntries = insurance.forLedger(month);

        // Clients split with their own partners (MF and equity) and every client with business this month.
        java.util.Set<Long> splitByShares = new java.util.LinkedHashSet<>(mfClients);
        splitByShares.addAll(eq.clientIds());
        java.util.Set<Long> all = new java.util.LinkedHashSet<>(splitByShares);
        fdEntries.forEach(x -> all.add(x.clientId()));
        bondEntries.forEach(x -> all.add(x.clientId()));
        aifEntries.forEach(x -> all.add(x.clientId()));
        loanEntries.forEach(x -> all.add(x.clientId()));
        giftEntries.forEach(x -> all.add(x.clientId()));
        preIpoEntries.forEach(x -> all.add(x.clientId()));
        pmsEntries.forEach(x -> all.add(x.clientId()));
        npsEntries.forEach(x -> all.add(x.clientId()));
        p2pEntries.forEach(x -> all.add(x.clientId()));
        insEntries.forEach(x -> all.add(x.clientId()));
        long notInMaster = all.size() - clients.rmsOn(List.copyOf(all), day).size();

        java.util.Set<String> missing = new java.util.TreeSet<>();
        shares.on(List.copyOf(splitByShares), day).values().forEach(list -> list.stream()
                .filter(s -> s.source() == ShareService.Source.NO_PARTNER_PERCENT).forEach(s -> missing.add(s.partnerName() + " (MF)")));
        Map<String, ShareService.ProductShare> seen = new HashMap<>();
        java.util.function.BiConsumer<Long, Product> need = (partnerId, product) -> {
            if (partnerId == null) return;
            ShareService.ProductShare ps = seen.computeIfAbsent(partnerId + product.code(), k -> shares.productShare(partnerId, product, day));
            if (ps.fraction() == null) missing.add(ps.partnerName() + " (" + PRODUCT_LABEL.get(product) + ")");
        };
        fdEntries.forEach(x -> need.accept(x.partnerId(), Product.FD));
        bondEntries.forEach(x -> need.accept(x.partnerId(), Product.BOND));
        // An AIF deal's own sharing % stands in for the partner's AIF %.
        aifEntries.stream().filter(x -> x.sharingPercent() == null).forEach(x -> need.accept(x.partnerId(), Product.AIF));
        loanEntries.forEach(x -> need.accept(x.partnerId(), Product.LOAN));
        giftEntries.forEach(x -> need.accept(x.partnerId(), Product.GIFT));
        preIpoEntries.stream().filter(x -> x.sharingPercent() == null).forEach(x -> need.accept(x.partnerId(), Product.PREIPO));
        pmsEntries.forEach(x -> need.accept(x.partnerId(), Product.PMS));
        npsEntries.forEach(x -> need.accept(x.partnerId(), Product.NPS));
        p2pEntries.forEach(x -> need.accept(x.partnerId(), Product.P2P));

        Map<String, Integer> entries = new LinkedHashMap<>();
        entries.put(FD, fdEntries.size());
        entries.put(BOND, bondEntries.size());
        entries.put(AIF, aifEntries.size());
        entries.put(LOAN, loanEntries.size());
        entries.put(GIFT, giftEntries.size());
        entries.put(PREIPO, preIpoEntries.size());
        entries.put(PMS, pmsEntries.size());
        entries.put(NPS, npsEntries.size());
        entries.put(P2P, p2pEntries.size());
        entries.put(INS, insEntries.size());
        boolean ready = period.status() != Period.Status.LOCKED && !mfClients.isEmpty() && notInMaster == 0 && missing.isEmpty()
                && eq.notInClients() == 0;
        return new Readiness(periodId, month, period.status().name(), !mfClients.isEmpty(), mfClients.size(), eq.uploaded(),
                eq.clientIds().size(), eq.notInClients(), entries, notInMaster, List.copyOf(missing), ready);
    }

    /** Voids the month's current split (its figures changed or it is being re-run). */
    @Transactional
    public void voidSplits(long periodId) {
        jdbc.sql("UPDATE brokerage_split SET voided = TRUE, voided_at = now() WHERE period_id = ? AND NOT voided").param(periodId).update();
        jdbc.sql("UPDATE attribution_run SET status = 'VOIDED' WHERE period_id = ? AND status = 'DONE'").param(periodId).update();
    }

    // ---- summary -------------------------------------------------------------------------------------------

    public record PartnerTotal(long partnerId, String partnerName, String role, long clients, BigDecimal amount) {
    }

    public record BranchTotal(String region, String branch, BigDecimal net, BigDecimal partners, BigDecimal epsilon) {
    }

    public record Summary(long periodId, YearMonth month, String status, long lines, long adjustments, BigDecimal net,
                          BigDecimal subBrokers, BigDecimal referrals, BigDecimal epsilon, List<PartnerTotal> byPartner,
                          List<BranchTotal> byBranch) {
    }

    /** Totals of the current split of a month (empty figures if it was not processed). */
    @Transactional(readOnly = true)
    public Summary summary(long periodId) {
        PeriodView p = periods.get(periodId);
        Map<String, Object> t = jdbc.sql("""
                        SELECT count(DISTINCT brokerage_line_id) AS lines,
                               count(DISTINCT brokerage_line_id) FILTER (WHERE is_adjustment) AS adjustments,
                               coalesce(sum(amount), 0) AS net,
                               coalesce(sum(amount) FILTER (WHERE partner_role = 'SUB_BROKER'), 0) AS sub_brokers,
                               coalesce(sum(amount) FILTER (WHERE partner_role = 'REFERRAL'), 0) AS referrals,
                               coalesce(sum(amount) FILTER (WHERE payee_type = 'COMPANY'), 0) AS epsilon
                        FROM brokerage_split WHERE period_id = ? AND NOT voided""")
                .param(periodId).query().singleRow();
        List<PartnerTotal> byPartner = jdbc.sql("""
                        SELECT partner_id, max(partner_name) AS name, partner_role, count(DISTINCT client_id) AS clients, sum(amount) AS amount
                        FROM brokerage_split WHERE period_id = ? AND NOT voided AND payee_type = 'PARTNER'
                        GROUP BY partner_id, partner_role ORDER BY sum(amount) DESC""")
                .param(periodId)
                .query((rs, n) -> new PartnerTotal(rs.getLong("partner_id"), rs.getString("name"), rs.getString("partner_role"),
                        rs.getLong("clients"), rs.getBigDecimal("amount")))
                .list();
        List<BranchTotal> byBranch = jdbc.sql("""
                        SELECT region_at_period, branch_at_period, sum(amount) AS net,
                               coalesce(sum(amount) FILTER (WHERE payee_type = 'PARTNER'), 0) AS partners,
                               coalesce(sum(amount) FILTER (WHERE payee_type = 'COMPANY'), 0) AS epsilon
                        FROM brokerage_split WHERE period_id = ? AND NOT voided
                        GROUP BY region_at_period, branch_at_period ORDER BY region_at_period, branch_at_period""")
                .param(periodId)
                .query((rs, n) -> new BranchTotal(rs.getString("region_at_period"), rs.getString("branch_at_period"), rs.getBigDecimal("net"),
                        rs.getBigDecimal("partners"), rs.getBigDecimal("epsilon")))
                .list();
        return new Summary(periodId, p.month(), p.status().name(), ((Number) t.get("lines")).longValue(),
                ((Number) t.get("adjustments")).longValue(), (BigDecimal) t.get("net"), (BigDecimal) t.get("sub_brokers"),
                (BigDecimal) t.get("referrals"), (BigDecimal) t.get("epsilon"), byPartner, byBranch);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** Where an RM sat on the day: names as they were. */
    record Placement(String rmName, String branch, String region, String rd) {
    }

    private static Map<Long, Placement> placements(OrgChart chart) {
        Map<Long, Placement> result = new HashMap<>();
        for (var r : chart.regions()) {
            String rd = r.rd() == null ? null : r.rd().name();
            for (var b : r.branches()) {
                for (var p : b.people()) result.put(p.id(), new Placement(p.name(), b.name(), r.name(), rd));
            }
        }
        for (var b : chart.branchesWithoutRegion()) {
            for (var p : b.people()) result.put(p.id(), new Placement(p.name(), b.name(), null, null));
        }
        for (var p : chart.peopleWithoutBranch()) result.put(p.id(), new Placement(p.name(), null, null, null));
        return result;
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
