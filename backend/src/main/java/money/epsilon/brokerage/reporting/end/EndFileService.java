package money.epsilon.brokerage.reporting.end;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import money.epsilon.brokerage.org.service.RegionService;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.reporting.end.EndWorkbook.BranchTotal;
import money.epsilon.brokerage.reporting.end.EndWorkbook.EndRow;
import money.epsilon.brokerage.reporting.end.EndWorkbook.RegionLayout;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the END file for a month from the stored split (v_line_split): never from today's masters. A LOCKED month gives
 * the final file; a PROCESSED month a DRAFT. Full PAN only for FINANCE / ADMIN. Every download is audited.
 */
@Service
public class EndFileService {

    static final String MF = "MF";
    static final String FD = "FD";
    static final String BOND = "BOND";
    static final String AIF = "AIF";
    static final String LOAN = "LOAN";
    static final String GIFT = "GIFT";
    static final String PREIPO = "PREIPO";
    static final String PMS = "PMS";
    static final String NPS = "NPS";
    static final String P2P = "P2P";
    static final String EQUITY = "EQUITY";
    static final String NO_BRANCH = "(no branch)";

    private final PeriodService periods;
    private final JdbcClient jdbc;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final money.epsilon.brokerage.client.service.ClientService clients;
    private final RegionService regions;
    private final money.epsilon.brokerage.fd.service.FdService fds;
    private final money.epsilon.brokerage.bond.service.BondService bonds;
    private final money.epsilon.brokerage.aif.service.AifService aifs;
    private final money.epsilon.brokerage.loan.service.LoanService loans;
    private final money.epsilon.brokerage.gift.service.GiftService gifts;
    private final money.epsilon.brokerage.preipo.service.PreIpoService preIpos;
    private final money.epsilon.brokerage.pms.service.PmsFeeService pmsFees;
    private final money.epsilon.brokerage.nps.service.NpsService npses;
    private final money.epsilon.brokerage.p2p.service.P2pService p2ps;

    EndFileService(PeriodService periods, JdbcClient jdbc, PanCipher panCipher, AuditService audit,
                   money.epsilon.brokerage.client.service.ClientService clients, RegionService regions,
                   money.epsilon.brokerage.fd.service.FdService fds, money.epsilon.brokerage.bond.service.BondService bonds,
                   money.epsilon.brokerage.aif.service.AifService aifs, money.epsilon.brokerage.loan.service.LoanService loans,
                   money.epsilon.brokerage.gift.service.GiftService gifts,
                   money.epsilon.brokerage.preipo.service.PreIpoService preIpos,
                   money.epsilon.brokerage.pms.service.PmsFeeService pmsFees, money.epsilon.brokerage.nps.service.NpsService npses,
                   money.epsilon.brokerage.p2p.service.P2pService p2ps) {
        this.aifs = aifs;
        this.loans = loans;
        this.gifts = gifts;
        this.preIpos = preIpos;
        this.pmsFees = pmsFees;
        this.npses = npses;
        this.p2ps = p2ps;
        this.fds = fds;
        this.bonds = bonds;
        this.clients = clients;
        this.regions = regions;
        this.periods = periods;
        this.jdbc = jdbc;
        this.panCipher = panCipher;
        this.audit = audit;
    }

    public record EndFile(String fileName, byte[] content, boolean draft) {
    }

    @Transactional // not read-only: the download is audited
    public EndFile build(long periodId) {
        PeriodView p = periods.get(periodId);
        if (p.status() == Period.Status.OPEN) throw new RuleViolationException("Process " + p.month() + " first; the END file comes from the split");
        boolean draft = p.status() != Period.Status.LOCKED;
        boolean fullPan = Roles.canSeeFullPan();
        Set<Long> rmChanged = clients.clientsWithRmChangeIn(p.fromDate(), p.toDate());
        Map<Long, YearMonth> monthOfPeriod = periods.list(MF).stream().collect(Collectors.toMap(PeriodView::id, PeriodView::month));

        List<EndRow> rows = jdbc.sql("""
                        SELECT * FROM v_line_split WHERE period_id = ? AND product_type IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(periodId, MF, FD, BOND, AIF, LOAN, GIFT, EQUITY, PREIPO, PMS, NPS, P2P)
                .query((rs, n) -> {
                    boolean fd = FD.equals(rs.getString("product_type"));
                    boolean bond = BOND.equals(rs.getString("product_type"));
                    String pan = rs.getString("pan_encrypted") == null ? null : panCipher.decrypt(rs.getString("pan_encrypted"));
                    Long adjusts = (Long) rs.getObject("adjusts_period_id");
                    long clientId = rs.getLong("client_id");
                    String remarks = adjusts != null ? "Adjustment entry for " + EndWorkbook.monYY(monthOfPeriod.get(adjusts))
                            : rmChanged.contains(clientId) ? "RM reassigned during period" : null;
                    EndRow row = new EndRow(rs.getString("region"), rs.getString("rd"), Objects.requireNonNullElse(rs.getString("branch"), NO_BRANCH),
                            rs.getString("rm"), rs.getString("sub_broker"), rs.getBigDecimal("sub_broker_pct"), rs.getString("referral"),
                            rs.getBigDecimal("referral_pct"), rs.getString("posp"), rs.getString("client_name"),
                            fullPan ? pan : PanCipher.mask(pan), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net"),
                            rs.getBigDecimal("sub_broker_amount"), rs.getBigDecimal("referral_amount"), rs.getBigDecimal("posp_amount"), remarks);
                    if (AIF.equals(rs.getString("product_type"))) {
                        // An AIF row (R-78): its gross revenue in the AIF column, the commitment as AUM, MF columns empty.
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), "AIF#" + rs.getLong("aif_entry_id"), null, null, null, rs.getBigDecimal("net"), null, null, null);
                    }
                    String type = rs.getString("product_type");
                    if (PMS.equals(type) || NPS.equals(type) || P2P.equals(type)) {
                        // A PMS / NPS / P2P row (R-85..R-87): its revenue excl. GST in its own column, MF columns empty; labelled below.
                        java.math.BigDecimal v = rs.getBigDecimal("net");
                        String tag = PMS.equals(type) ? "PMS#" + rs.getLong("pms_fee_id") : NPS.equals(type) ? "NPS#" + rs.getLong("nps_entry_id")
                                : "P2P#" + rs.getLong("p2p_entry_id");
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), tag, null, null, null, null, null, null, null, null,
                                PMS.equals(type) ? v : null, NPS.equals(type) ? v : null, P2P.equals(type) ? v : null);
                    }
                    if (PREIPO.equals(rs.getString("product_type"))) {
                        // A Pre-IPO row (R-93): its brokerage excl. GST in the Pre-IPO column, the deal amount as AUM, MF columns empty.
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), "PREIPO#" + rs.getLong("preipo_entry_id"), null, null, null, null, null, null,
                                null, rs.getBigDecimal("net"));
                    }
                    if (EQUITY.equals(rs.getString("product_type"))) {
                        // An equity row (R-89): the client's franchise share in the Equity column, MF columns empty, no remark (as in the END).
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), row.remarks(), null, null, null, null, null, null, rs.getBigDecimal("net"));
                    }
                    if (GIFT.equals(rs.getString("product_type"))) {
                        // A GIFT City row (R-82): its brokerage excl. GST in the GIFT CITY column, the rupee amount as AUM.
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), "GIFT#" + rs.getLong("gift_entry_id"), null, null, null, null, null,
                                rs.getBigDecimal("net"), null);
                    }
                    if (LOAN.equals(rs.getString("product_type"))) {
                        // A loan row (R-80): its payout excl. GST in the Loans column, the sanctioned amount as AUM, MF columns empty.
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), "LOAN#" + rs.getLong("loan_entry_id"), null, null, null, null, rs.getBigDecimal("net"), null, null);
                    }
                    if (bond) {
                        // A bond row (R-74): its gross revenue in the Bonds column, the consideration as AUM, MF columns empty.
                        return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                                row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                                row.referralAmount(), row.pospAmount(), "BOND#" + rs.getLong("bond_entry_id"), null, null, rs.getBigDecimal("net"), null, null, null, null);
                    }
                    if (!fd) return row;
                    // An FD row (R-74): its commission in the FD column, the amount invested as AUM, MF columns empty.
                    return new EndRow(row.region(), row.rd(), row.branch(), row.rm(), row.subBroker(), row.subBrokerPct(), row.referral(),
                            row.referralPct(), row.posp(), row.client(), row.pan(), null, null, null, row.subBrokerAmount(),
                            row.referralAmount(), row.pospAmount(), "FD#" + rs.getLong("fd_entry_id"), null, rs.getBigDecimal("net"), null, null, null, null, null);
                })
                .list().stream()
                .sorted(Comparator.comparing((EndRow e) -> Objects.toString(e.region(), "")).thenComparing(EndRow::branch)
                        .thenComparing(e -> Objects.toString(e.rm(), "")).thenComparing(e -> Objects.toString(e.client(), "")))
                .toList();
        if (rows.isEmpty()) throw new RuleViolationException(p.month() + " has no split to export");
        rows = withOtherLabels(withPreIpoLabels(withGiftLabels(withLoanLabels(withAifLabels(withBondLabels(withFdLabels(rows)))))));

        PeriodView previous = periods.list(MF).stream()
                .filter(x -> x.status() == Period.Status.LOCKED && x.month().isBefore(p.month()))
                .max(Comparator.comparing(PeriodView::month)).orElse(null);
        List<BranchTotal> prev = previous == null ? List.of() : jdbc.sql("""
                        SELECT region, coalesce(branch, ?) AS branch, sum(net) AS gross, sum(epsilon_amount) AS net
                        FROM v_line_split WHERE period_id = ? AND product_type IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) GROUP BY region, coalesce(branch, ?)""")
                .params(NO_BRANCH, previous.id(), MF, FD, BOND, AIF, LOAN, GIFT, EQUITY, PREIPO, PMS, NPS, P2P, NO_BRANCH)
                .query((rs, n) -> new BranchTotal(rs.getString("region"), rs.getString("branch"), rs.getBigDecimal("gross"), rs.getBigDecimal("net")))
                .list();

        List<RegionLayout> layout = regions.list().stream().map(r -> new RegionLayout(r.name(), r.endBranchRows())).toList();
        byte[] content = EndWorkbook.build(new EndWorkbook.Input(p.month(), draft, rows, previous == null ? null : previous.month(), prev, layout));
        String name = (draft ? "DRAFT_" : "") + EndWorkbook.fileName(p.month());
        BigDecimal net = rows.stream().map(EndRow::totalRev).reduce(BigDecimal.ZERO, BigDecimal::add);
        audit.record("END_FILE", periodId, "EXPORT", null, new ExportAudit(p.month(), draft, rows.size(), net, fullPan));
        return new EndFile(name, content, draft);
    }

    /** FD rows carry "FD#id" until here: they get the FD's company and number as REMARKS, and its amount as AUM. */
    private List<EndRow> withFdLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("FD#")).map(r -> Long.parseLong(r.substring(3))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.fd.service.FdService.FdLabel> labels = fds.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("FD#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(3)));
            String remark = l == null ? "FD" : "FD: " + l.fdName() + (l.fdNo() == null ? "" : " #" + l.fdNo());
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), remark,
                    l == null ? null : l.amount(), e.fd(), null, null, null, null, null);
        }).toList();
    }

    /** Bond rows carry "BOND#id" until here: they get the bond name and ISIN as REMARKS, and the consideration as AUM. */
    private List<EndRow> withBondLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("BOND#")).map(r -> Long.parseLong(r.substring(5))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.bond.service.BondService.BondLabel> labels = bonds.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("BOND#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(5)));
            String remark = l == null ? "Bond" : "Bond: " + l.bondName() + (l.isin() == null ? "" : " " + l.isin());
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), remark,
                    l == null ? null : l.consideration(), null, e.bonds(), null, null, null, null);
        }).toList();
    }

    /** AIF rows carry "AIF#id" until here: they get the fund name as REMARKS, and the commitment as AUM. */
    private List<EndRow> withAifLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("AIF#")).map(r -> Long.parseLong(r.substring(4))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.aif.service.AifService.AifLabel> labels = aifs.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("AIF#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(4)));
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(),
                    l == null ? "AIF" : "AIF: " + l.fundName(), l == null ? null : l.commitment(), null, null, e.aif(), null, null, null);
        }).toList();
    }

    /** Loan rows carry "LOAN#id" until here: they get lender and loan type as REMARKS, and the sanctioned amount as AUM. */
    private List<EndRow> withLoanLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("LOAN#")).map(r -> Long.parseLong(r.substring(5))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.loan.service.LoanService.LoanLabel> labels = loans.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("LOAN#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(5)));
            String remark = l == null ? "Loan" : "Loan: " + l.lender() + " " + l.loanType() + (l.trail() ? " (trail)" : "");
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), remark,
                    l == null ? null : l.sanctionedAmount(), null, null, null, e.loans(), null, null);
        }).toList();
    }

    /**
     * PMS, NPS and P2P rows carry "PMS#id" / "NPS#id" / "P2P#id" until here: they get the AMC and strategy, the POP, or the
     * platform and scheme as REMARKS; NPS and P2P rows also the amount invested as AUM (PMS rows have none, as in the END).
     */
    private List<EndRow> withOtherLabels(List<EndRow> rows) {
        java.util.function.Function<String, List<Long>> ids = prefix -> rows.stream().map(EndRow::remarks)
                .filter(r -> r != null && r.startsWith(prefix)).map(r -> Long.parseLong(r.substring(prefix.length()))).toList();
        List<Long> pmsIds = ids.apply("PMS#"), npsIds = ids.apply("NPS#"), p2pIds = ids.apply("P2P#");
        if (pmsIds.isEmpty() && npsIds.isEmpty() && p2pIds.isEmpty()) return rows;
        Map<Long, String> pmsLabels = pmsIds.isEmpty() ? Map.of() : pmsFees.labels(pmsIds);
        var npsLabels = npsIds.isEmpty() ? Map.<Long, money.epsilon.brokerage.nps.service.NpsService.NpsLabel>of() : npses.labels(npsIds);
        var p2pLabels = p2pIds.isEmpty() ? Map.<Long, money.epsilon.brokerage.p2p.service.P2pService.P2pLabel>of() : p2ps.labels(p2pIds);
        return rows.stream().map(e -> {
            String rem = e.remarks();
            if (rem == null) return e;
            String remark;
            java.math.BigDecimal aum = null;
            if (rem.startsWith("PMS#")) {
                remark = pmsLabels.getOrDefault(Long.parseLong(rem.substring(4)), "PMS");
            } else if (rem.startsWith("NPS#")) {
                var l = npsLabels.get(Long.parseLong(rem.substring(4)));
                remark = l == null ? "NPS" : "NPS: " + l.pop();
                aum = l == null ? null : l.amount();
            } else if (rem.startsWith("P2P#")) {
                var l = p2pLabels.get(Long.parseLong(rem.substring(4)));
                remark = l == null ? "P2P" : "P2P: " + l.item();
                aum = l == null ? null : l.amount();
            } else {
                return e;
            }
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), remark,
                    aum, null, null, null, null, null, null, null, e.pms(), e.nps(), e.p2p());
        }).toList();
    }

    /** Pre-IPO rows carry "PREIPO#id" until here: they get buy / buyback, the scrip and ISIN as REMARKS, and the deal amount as AUM. */
    private List<EndRow> withPreIpoLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("PREIPO#")).map(r -> Long.parseLong(r.substring(7))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.preipo.service.PreIpoService.PreIpoLabel> labels = preIpos.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("PREIPO#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(7)));
            String remark = l == null ? "Pre-IPO" : (l.kind() == money.epsilon.brokerage.preipo.domain.PreIpoEntry.Kind.BUY ? "Pre-IPO buy: " : "Pre-IPO buyback: ")
                    + l.scripName() + (l.isin() == null ? "" : " " + l.isin());
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(), remark,
                    l == null ? null : l.amount(), null, null, null, null, null, null, e.preIpo());
        }).toList();
    }

    /** GIFT City rows carry "GIFT#id" until here: they get the fund as REMARKS, and the rupee amount as AUM. */
    private List<EndRow> withGiftLabels(List<EndRow> rows) {
        List<Long> ids = rows.stream().map(EndRow::remarks).filter(r -> r != null && r.startsWith("GIFT#")).map(r -> Long.parseLong(r.substring(5))).toList();
        if (ids.isEmpty()) return rows;
        Map<Long, money.epsilon.brokerage.gift.service.GiftService.GiftLabel> labels = gifts.labels(ids);
        return rows.stream().map(e -> {
            if (e.remarks() == null || !e.remarks().startsWith("GIFT#")) return e;
            var l = labels.get(Long.parseLong(e.remarks().substring(5)));
            return new EndRow(e.region(), e.rd(), e.branch(), e.rm(), e.subBroker(), e.subBrokerPct(), e.referral(), e.referralPct(), e.posp(),
                    e.client(), e.pan(), null, null, null, e.subBrokerAmount(), e.referralAmount(), e.pospAmount(),
                    l == null ? "GIFT City" : "GIFT City: " + l.fund(), l == null ? null : l.amountInr(), null, null, null, null, e.gift(), null);
        }).toList();
    }

    record ExportAudit(YearMonth month, boolean draft, int rows, BigDecimal net, boolean fullPan) {
    }
}
