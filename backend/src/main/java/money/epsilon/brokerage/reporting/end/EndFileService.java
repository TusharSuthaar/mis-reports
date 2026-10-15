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
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import money.epsilon.brokerage.reporting.end.EndWorkbook.BranchTotal;
import money.epsilon.brokerage.reporting.end.EndWorkbook.EndRow;
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
    static final String NO_BRANCH = "(no branch)";

    private final PeriodService periods;
    private final JdbcClient jdbc;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final money.epsilon.brokerage.client.service.ClientService clients;

    EndFileService(PeriodService periods, JdbcClient jdbc, PanCipher panCipher, AuditService audit,
                   money.epsilon.brokerage.client.service.ClientService clients) {
        this.clients = clients;
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
                        SELECT * FROM v_line_split WHERE period_id = ? AND product_type = ?""")
                .params(periodId, MF)
                .query((rs, n) -> {
                    String pan = rs.getString("pan_encrypted") == null ? null : panCipher.decrypt(rs.getString("pan_encrypted"));
                    Long adjusts = (Long) rs.getObject("adjusts_period_id");
                    long clientId = rs.getLong("client_id");
                    String remarks = adjusts != null ? "Adjustment entry for " + EndWorkbook.monYY(monthOfPeriod.get(adjusts))
                            : rmChanged.contains(clientId) ? "RM reassigned during period" : null;
                    return new EndRow(rs.getString("region"), rs.getString("rd"), Objects.requireNonNullElse(rs.getString("branch"), NO_BRANCH),
                            rs.getString("rm"), rs.getString("sub_broker"), rs.getBigDecimal("sub_broker_pct"), rs.getString("referral"),
                            rs.getBigDecimal("referral_pct"), rs.getString("posp"), rs.getString("client_name"),
                            fullPan ? pan : PanCipher.mask(pan), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net"),
                            rs.getBigDecimal("sub_broker_amount"), rs.getBigDecimal("referral_amount"), rs.getBigDecimal("posp_amount"), remarks);
                })
                .list().stream()
                .sorted(Comparator.comparing((EndRow e) -> Objects.toString(e.region(), "")).thenComparing(EndRow::branch)
                        .thenComparing(e -> Objects.toString(e.rm(), "")).thenComparing(e -> Objects.toString(e.client(), "")))
                .toList();
        if (rows.isEmpty()) throw new RuleViolationException(p.month() + " has no split to export");

        PeriodView previous = periods.list(MF).stream()
                .filter(x -> x.status() == Period.Status.LOCKED && x.month().isBefore(p.month()))
                .max(Comparator.comparing(PeriodView::month)).orElse(null);
        List<BranchTotal> prev = previous == null ? List.of() : jdbc.sql("""
                        SELECT region, coalesce(branch, ?) AS branch, sum(net) AS gross, sum(epsilon_amount) AS net
                        FROM v_line_split WHERE period_id = ? AND product_type = ? GROUP BY region, coalesce(branch, ?)""")
                .params(NO_BRANCH, previous.id(), MF, NO_BRANCH)
                .query((rs, n) -> new BranchTotal(rs.getString("region"), rs.getString("branch"), rs.getBigDecimal("gross"), rs.getBigDecimal("net")))
                .list();

        byte[] content = EndWorkbook.build(new EndWorkbook.Input(p.month(), draft, rows, previous == null ? null : previous.month(), prev));
        String name = (draft ? "DRAFT_" : "") + EndWorkbook.fileName(p.month());
        BigDecimal net = rows.stream().map(EndRow::mfNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        audit.record("END_FILE", periodId, "EXPORT", null, new ExportAudit(p.month(), draft, rows.size(), net, fullPan));
        return new EndFile(name, content, draft);
    }

    record ExportAudit(YearMonth month, boolean draft, int rows, BigDecimal net, boolean fullPan) {
    }
}
