package money.epsilon.brokerage.ledger.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The brokerage ledger: one line per client per product per period, append-only. A line is never edited or deleted;
 * a replaced upload's lines are voided (the database enforces both).
 */
@Service
public class LedgerService {

    private final JdbcClient jdbc;

    LedgerService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record LineInput(long clientId, long stagedRowId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    public record Totals(long lines, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** Voids the own active lines of the month (not corrections posted into it for other months). @return how many */
    @Transactional
    public int voidActive(long periodId, String productType) {
        return jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = ? AND NOT voided AND adjusts_period_id IS NULL")
                .params(periodId, productType).update();
    }

    @Transactional
    public void write(long periodId, String productType, long uploadId, List<LineInput> lines) {
        for (LineInput l : lines) {
            jdbc.sql("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, upload_id, staged_row_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(periodId, l.clientId(), productType, l.gross(), l.gst(), l.net(), uploadId, l.stagedRowId())
                    .update();
        }
    }

    /**
     * An active line, as the split needs it. {@code adjustsPeriodId} is set on corrections to a locked month;
     * {@code fdEntryId} on a line that comes from an FD entry (R-74).
     */
    public record ActiveLine(long id, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net, Long adjustsPeriodId, Long fdEntryId,
                             Long bondEntryId, Long aifEntryId) {
    }

    /** One AIF commitment of the month: gross = invoice (incl. GST), gst, net = gross revenue excl. GST. */
    public record AifLineInput(long aifEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's AIF lines become exactly {@code lines} (R-74), like FD and bond lines. @return how many were written */
    @Transactional
    public int replaceAifLines(long periodId, List<AifLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'AIF' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        for (AifLineInput l : lines) {
            jdbc.sql("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, aif_entry_id)
                            VALUES (?, ?, 'AIF', ?, ?, ?, ?)""")
                    .params(periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.aifEntryId())
                    .update();
        }
        return lines.size();
    }

    /** One bond deal of the month: gross = invoice (incl. GST), gst, net = gross revenue excl. GST. */
    public record BondLineInput(long bondEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's bond lines become exactly {@code lines} (R-74), like FD lines. @return how many were written */
    @Transactional
    public int replaceBondLines(long periodId, List<BondLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'BOND' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        for (BondLineInput l : lines) {
            jdbc.sql("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, bond_entry_id)
                            VALUES (?, ?, 'BOND', ?, ?, ?, ?)""")
                    .params(periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.bondEntryId())
                    .update();
        }
        return lines.size();
    }

    /** One FD booked in the month: gross = invoice (incl. GST), gst, net = the commission excl. GST. */
    public record FdLineInput(long fdEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /**
     * The month's FD lines become exactly {@code lines}: the active ones are voided and these written (R-74). Lines are
     * append-only (R-44), so a re-run leaves the earlier ones voided, never changed. @return how many were written
     */
    @Transactional
    public int replaceFdLines(long periodId, List<FdLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'FD' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        for (FdLineInput l : lines) {
            jdbc.sql("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, fd_entry_id)
                            VALUES (?, ?, 'FD', ?, ?, ?, ?)""")
                    .params(periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.fdEntryId())
                    .update();
        }
        return lines.size();
    }

    @Transactional(readOnly = true)
    public List<ActiveLine> activeLines(long periodId, String productType) {
        return jdbc.sql("""
                        SELECT id, client_id, gross, gst, net, adjusts_period_id, fd_entry_id, bond_entry_id, aif_entry_id FROM brokerage_line
                        WHERE period_id = ? AND product_type = ? AND NOT voided ORDER BY id""")
                .params(periodId, productType)
                .query((rs, n) -> new ActiveLine(rs.getLong("id"), rs.getLong("client_id"), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"),
                        rs.getBigDecimal("net"), (Long) rs.getObject("adjusts_period_id"), (Long) rs.getObject("fd_entry_id"),
                        (Long) rs.getObject("bond_entry_id"), (Long) rs.getObject("aif_entry_id")))
                .list();
    }

    /** period id → the clients with an active line of their own (not a correction) in that month. */
    @Transactional(readOnly = true)
    public Map<Long, java.util.Set<Long>> activeClientsByPeriod(String productType) {
        Map<Long, java.util.Set<Long>> result = new java.util.HashMap<>();
        jdbc.sql("SELECT DISTINCT period_id, client_id FROM brokerage_line WHERE product_type = ? AND NOT voided AND adjusts_period_id IS NULL")
                .param(productType).query((rs, n) -> Map.entry(rs.getLong(1), rs.getLong(2))).list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new java.util.HashSet<>()).add(e.getValue()));
        return result;
    }

    /** What a month finally says per client: its own lines plus every correction posted for it later. */
    @Transactional(readOnly = true)
    public Map<Long, Amounts> effectiveByClient(long periodId, String productType) {
        return jdbc.sql("""
                        SELECT client_id, sum(gross) AS gross, sum(gst) AS gst, sum(net) AS net FROM brokerage_line
                        WHERE product_type = ? AND NOT voided
                          AND ((period_id = ? AND adjusts_period_id IS NULL) OR adjusts_period_id = ?)
                        GROUP BY client_id""")
                .params(productType, periodId, periodId)
                .query((rs, n) -> Map.entry(rs.getLong("client_id"), new Amounts(rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public record Amounts(BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** A correction line for one client. */
    public record Adjustment(long clientId, Long stagedRowId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** Posts corrections to the locked month {@code adjustsPeriodId} as lines of month {@code periodId}. */
    @Transactional
    public void writeAdjustments(long periodId, String productType, long uploadId, long adjustsPeriodId, List<Adjustment> adjustments) {
        for (Adjustment a : adjustments) {
            jdbc.sql("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, upload_id, staged_row_id, adjusts_period_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(periodId, a.clientId(), productType, a.gross(), a.gst(), a.net(), uploadId, a.stagedRowId(), adjustsPeriodId)
                    .update();
        }
    }

    /** Sums of the active lines of a period and product. */
    @Transactional(readOnly = true)
    public Totals totals(long periodId, String productType) {
        return jdbc.sql("""
                        SELECT count(*) AS lines, coalesce(sum(gross), 0) AS gross, coalesce(sum(gst), 0) AS gst, coalesce(sum(net), 0) AS net
                        FROM brokerage_line WHERE period_id = ? AND product_type = ? AND NOT voided""")
                .params(periodId, productType)
                .query((rs, n) -> new Totals(rs.getLong("lines"), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net")))
                .single();
    }

    /** period id → totals of its active lines, for all periods of a product that have any. */
    @Transactional(readOnly = true)
    public Map<Long, Totals> totalsByPeriod(String productType) {
        return jdbc.sql("""
                        SELECT period_id, count(*) AS lines, sum(gross) AS gross, sum(gst) AS gst, sum(net) AS net
                        FROM brokerage_line WHERE product_type = ? AND NOT voided GROUP BY period_id""")
                .param(productType)
                .query((rs, n) -> Map.entry(rs.getLong("period_id"),
                        new Totals(rs.getLong("lines"), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
