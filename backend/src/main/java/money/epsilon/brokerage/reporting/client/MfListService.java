package money.epsilon.brokerage.reporting.client;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LedgerService.ActiveLine;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.reporting.client.ClientBrokerageService.PartnerAmount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF brokerage of every client across months, for the MF tab of Product entries (R-81): processed months from the stored
 * split (with partners and what Epsilon keeps), saved but unprocessed months from the ledger (no split yet). Newest month
 * first, then by client.
 */
@Service
public class MfListService {

    static final int MAX_ROWS = 3000;

    private final PeriodService periods;
    private final LedgerService ledger;
    private final ClientService clients;
    private final EmployeeService employees;
    private final PanCipher panCipher;
    private final JdbcClient jdbc;

    MfListService(PeriodService periods, LedgerService ledger, ClientService clients, EmployeeService employees, PanCipher panCipher, JdbcClient jdbc) {
        this.periods = periods;
        this.ledger = ledger;
        this.clients = clients;
        this.employees = employees;
        this.panCipher = panCipher;
        this.jdbc = jdbc;
    }

    /** Every part optional. {@code q}: client name (part), full PAN or client code. */
    public record Filter(YearMonth from, YearMonth to, String q, Long rmId, Long partnerId) {
    }

    public record Row(long lineId, YearMonth month, Period.Status status, YearMonth adjustsMonth, long clientId, String clientName, String pan,
                      String rm, String branch, String region, BigDecimal gross, BigDecimal gst, BigDecimal net, List<PartnerAmount> partners,
                      BigDecimal partnersTotal, BigDecimal epsilon) {
    }

    public record Totals(long rows, BigDecimal gross, BigDecimal gst, BigDecimal net, BigDecimal partners, BigDecimal epsilon) {
    }

    /** {@code truncated}: more rows matched than are listed (the totals cover them all). */
    public record MfList(List<Row> rows, Totals totals, boolean truncated) {
    }

    @Transactional(readOnly = true)
    public MfList search(Filter f) {
        boolean fullPan = Roles.canSeeFullPan();
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        Set<Long> codeHits = new HashSet<>();
        if (!q.isEmpty()) {
            codeHits.addAll(clients.idsByExternalCode("FINESSE", List.of(q)).values());
            codeHits.addAll(clients.idsByExternalCode(ClientService.INVESTWELL, List.of(q)).values());
        }
        Map<Long, PeriodView> byId = periods.list("MF").stream().collect(Collectors.toMap(PeriodView::id, Function.identity()));
        List<Found> rows = new ArrayList<>();
        for (PeriodView p : byId.values()) {
            if (f.from() != null && p.month().isBefore(f.from())) continue;
            if (f.to() != null && p.month().isAfter(f.to())) continue;
            if (p.status() == Period.Status.OPEN) {
                if (f.partnerId() == null) rows.addAll(unsplit(p, byId, f.rmId(), fullPan));
            } else {
                rows.addAll(split(p, byId, f, fullPan));
            }
        }
        // A PAN typed in full finds the row even when the user sees PANs masked.
        List<Row> matched = rows.stream().filter(x -> q.isEmpty() || codeHits.contains(x.row().clientId())
                        || (x.row().clientName() != null && x.row().clientName().toUpperCase().contains(q)) || q.equals(x.fullPan()))
                .map(Found::row)
                .sorted(Comparator.comparing(Row::month, Comparator.reverseOrder()).thenComparing(r -> r.clientName() == null ? "" : r.clientName()))
                .toList();
        BigDecimal z = BigDecimal.ZERO;
        Totals totals = new Totals(matched.size(), sum(matched, Row::gross), sum(matched, Row::gst), sum(matched, Row::net),
                matched.stream().map(Row::partnersTotal).filter(java.util.Objects::nonNull).reduce(z, BigDecimal::add),
                matched.stream().map(Row::epsilon).filter(java.util.Objects::nonNull).reduce(z, BigDecimal::add));
        return new MfList(matched.size() > MAX_ROWS ? matched.subList(0, MAX_ROWS) : matched, totals, matched.size() > MAX_ROWS);
    }

    /** A row with the client's full PAN, for matching a typed PAN only (never sent). */
    private record Found(Row row, String fullPan) {
    }

    private static BigDecimal sum(List<Row> rows, Function<Row, BigDecimal> f) {
        return rows.stream().map(f).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Found> split(PeriodView p, Map<Long, PeriodView> byId, Filter f, boolean fullPan) {
        return jdbc.sql("""
                        SELECT * FROM v_line_split WHERE period_id = :period AND product_type = 'MF'
                          AND (CAST(:rm AS BIGINT) IS NULL OR rm_id = :rm)
                          AND (CAST(:partner AS BIGINT) IS NULL OR sub_broker_id = :partner OR referral_id = :partner OR posp_id = :partner)""")
                .param("period", p.id()).param("rm", f.rmId()).param("partner", f.partnerId())
                .query((rs, n) -> {
                    List<PartnerAmount> partners = new ArrayList<>();
                    if (rs.getString("sub_broker") != null) {
                        partners.add(new PartnerAmount("Sub-broker", rs.getString("sub_broker"), rs.getBigDecimal("sub_broker_pct"), rs.getBigDecimal("sub_broker_amount")));
                    }
                    if (rs.getString("referral") != null) {
                        partners.add(new PartnerAmount("Referral", rs.getString("referral"), rs.getBigDecimal("referral_pct"), rs.getBigDecimal("referral_amount")));
                    }
                    if (rs.getString("posp") != null) partners.add(new PartnerAmount("POSP", rs.getString("posp"), null, rs.getBigDecimal("posp_amount")));
                    String pan = rs.getString("pan_encrypted") == null ? null : panCipher.decrypt(rs.getString("pan_encrypted"));
                    long lineId = rs.getLong("line_id");
                    Long adjusts = (Long) rs.getObject("adjusts_period_id");
                    return new Found(new Row(lineId, p.month(), p.status(), adjusts == null || byId.get(adjusts) == null ? null : byId.get(adjusts).month(),
                            rs.getLong("client_id"), rs.getString("client_name"), pan == null ? null : fullPan ? pan : PanCipher.mask(pan),
                            rs.getString("rm"), rs.getString("branch"), rs.getString("region"), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"),
                            rs.getBigDecimal("net"), partners, partners.stream().map(PartnerAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                            rs.getBigDecimal("epsilon_amount")), pan);
                })
                .list();
    }

    /** A saved month not processed yet: its lines, with each client's RM on the month's last day; no split. */
    private List<Found> unsplit(PeriodView p, Map<Long, PeriodView> byId, Long rmFilter, boolean fullPan) {
        List<ActiveLine> lines = ledger.activeLines(p.id(), "MF");
        if (lines.isEmpty()) return List.of();
        Set<Long> ids = lines.stream().map(ActiveLine::clientId).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(ids);
        Map<Long, String> pans = clients.pans(ids);
        Map<Long, Long> rmOf = clients.rmsOn(ids, p.toDate());
        Map<Long, EmployeeView> people = new HashMap<>();
        employees.list().forEach(e -> people.put(e.id(), e));
        List<Found> result = new ArrayList<>();
        for (ActiveLine l : lines) {
            Long rmId = rmOf.get(l.clientId());
            if (rmFilter != null && !rmFilter.equals(rmId)) continue;
            EmployeeView rm = rmId == null ? null : people.get(rmId);
            String pan = pans.get(l.clientId());
            result.add(new Found(new Row(l.id(), p.month(), p.status(),
                    l.adjustsPeriodId() == null || byId.get(l.adjustsPeriodId()) == null ? null : byId.get(l.adjustsPeriodId()).month(),
                    l.clientId(), names.get(l.clientId()), pan == null ? null : fullPan ? pan : PanCipher.mask(pan),
                    rm == null ? null : rm.name(), rm == null ? null : rm.branchName(), null, l.gross(), l.gst(), l.net(), List.of(), null, null), pan));
        }
        return result;
    }
}
