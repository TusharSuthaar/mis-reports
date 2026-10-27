package money.epsilon.brokerage.ledger.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    LedgerService(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts all rows in one round trip (a month has thousands of lines). */
    private <T> void batch(String sql, List<T> items, java.util.function.Function<T, Object[]> args) {
        if (!items.isEmpty()) jdbcTemplate.batchUpdate(sql, items.stream().map(args).toList());
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
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, upload_id, staged_row_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), productType, l.gross(), l.gst(), l.net(), uploadId, l.stagedRowId()});
    }

    /**
     * An active line, as the split needs it. {@code adjustsPeriodId} is set on corrections to a locked month;
     * {@code fdEntryId} on a line that comes from an FD entry (R-74).
     */
    public record ActiveLine(long id, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net, Long adjustsPeriodId, Long fdEntryId,
                             Long bondEntryId, Long aifEntryId, Long loanEntryId, Long giftEntryId, Long preIpoEntryId, Long pmsFeeId,
                             Long npsEntryId, Long p2pEntryId) {
    }

    /** One line of the month from PMS (a client's typed revenue, no GST), NPS or P2P (R-85..R-87). */
    public record OtherLineInput(long sourceId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's PMS lines become exactly {@code lines}, like FD lines. */
    @Transactional
    public int replacePmsLines(long periodId, List<OtherLineInput> lines) {
        return replaceOther(periodId, "PMS", "pms_fee_id", lines);
    }

    @Transactional
    public int replaceNpsLines(long periodId, List<OtherLineInput> lines) {
        return replaceOther(periodId, "NPS", "nps_entry_id", lines);
    }

    @Transactional
    public int replaceP2pLines(long periodId, List<OtherLineInput> lines) {
        return replaceOther(periodId, "P2P", "p2p_entry_id", lines);
    }

    private int replaceOther(long periodId, String product, String sourceColumn, List<OtherLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = ? AND NOT voided AND adjusts_period_id IS NULL")
                .params(periodId, product).update();
        batch("INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, " + sourceColumn + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                lines, l -> new Object[]{periodId, l.clientId(), product, l.gross(), l.gst(), l.net(), l.sourceId()});
        return lines.size();
    }

    /** One Pre-IPO deal of the month: gross = invoice (incl. GST), gst, net = brokerage excl. GST (R-93). */
    public record PreIpoLineInput(long preIpoEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's Pre-IPO lines become exactly {@code lines}, like FD lines. @return how many were written */
    @Transactional
    public int replacePreIpoLines(long periodId, List<PreIpoLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'PREIPO' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, preipo_entry_id)
                            VALUES (?, ?, 'PREIPO', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.preIpoEntryId()});
        return lines.size();
    }

    /** One client's equity brokerage of the month (R-89): the franchise share, no GST (gross = net). */
    public record EquityLineInput(long equityUploadId, long clientId, BigDecimal net) {
    }

    /** The month's equity lines become exactly {@code lines}, like FD lines. @return how many were written */
    @Transactional
    public int replaceEquityLines(long periodId, List<EquityLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'EQUITY' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, equity_upload_id)
                            VALUES (?, ?, 'EQUITY', ?, 0, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.net(), l.net(), l.equityUploadId()});
        return lines.size();
    }

    /** One GIFT City entry of the month with brokerage: gross = invoice (incl. GST), gst, net = brokerage excl. GST. */
    public record GiftLineInput(long giftEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's GIFT City lines become exactly {@code lines} (R-82), like FD lines. @return how many were written */
    @Transactional
    public int replaceGiftLines(long periodId, List<GiftLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'GIFT' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, gift_entry_id)
                            VALUES (?, ?, 'GIFT', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.giftEntryId()});
        return lines.size();
    }

    /** One loan payout of the month: gross = invoice (incl. GST), gst, net = the payout excl. GST. */
    public record LoanLineInput(long loanEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's loan lines become exactly {@code lines} (R-74, R-80), like FD lines. @return how many were written */
    @Transactional
    public int replaceLoanLines(long periodId, List<LoanLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'LOAN' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, loan_entry_id)
                            VALUES (?, ?, 'LOAN', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.loanEntryId()});
        return lines.size();
    }

    /** One AIF commitment of the month: gross = invoice (incl. GST), gst, net = gross revenue excl. GST. */
    public record AifLineInput(long aifEntryId, long clientId, BigDecimal gross, BigDecimal gst, BigDecimal net) {
    }

    /** The month's AIF lines become exactly {@code lines} (R-74), like FD and bond lines. @return how many were written */
    @Transactional
    public int replaceAifLines(long periodId, List<AifLineInput> lines) {
        jdbc.sql("UPDATE brokerage_line SET voided = TRUE, voided_at = now() WHERE period_id = ? AND product_type = 'AIF' AND NOT voided AND adjusts_period_id IS NULL")
                .param(periodId).update();
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, aif_entry_id)
                            VALUES (?, ?, 'AIF', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.aifEntryId()});
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
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, bond_entry_id)
                            VALUES (?, ?, 'BOND', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.bondEntryId()});
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
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, fd_entry_id)
                            VALUES (?, ?, 'FD', ?, ?, ?, ?)""", lines, l -> new Object[]{periodId, l.clientId(), l.gross(), l.gst(), l.net(), l.fdEntryId()});
        return lines.size();
    }

    /** One active line of a client, for the client's page. */
    public record ClientLine(long id, long periodId, String productType, BigDecimal gross, BigDecimal gst, BigDecimal net, Long adjustsPeriodId) {
    }

    /** The client's active lines of a product, every month (R-09). */
    @Transactional(readOnly = true)
    public List<ClientLine> clientLines(long clientId, String productType) {
        return jdbc.sql("""
                        SELECT id, period_id, product_type, gross, gst, net, adjusts_period_id FROM brokerage_line
                        WHERE client_id = ? AND product_type = ? AND NOT voided ORDER BY period_id, id""")
                .params(clientId, productType)
                .query((rs, n) -> new ClientLine(rs.getLong("id"), rs.getLong("period_id"), rs.getString("product_type"), rs.getBigDecimal("gross"),
                        rs.getBigDecimal("gst"), rs.getBigDecimal("net"), (Long) rs.getObject("adjusts_period_id")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<ActiveLine> activeLines(long periodId, String productType) {
        return jdbc.sql("""
                        SELECT id, client_id, gross, gst, net, adjusts_period_id, fd_entry_id, bond_entry_id, aif_entry_id, loan_entry_id, gift_entry_id, preipo_entry_id, pms_fee_id, nps_entry_id, p2p_entry_id FROM brokerage_line
                        WHERE period_id = ? AND product_type = ? AND NOT voided ORDER BY id""")
                .params(periodId, productType)
                .query((rs, n) -> new ActiveLine(rs.getLong("id"), rs.getLong("client_id"), rs.getBigDecimal("gross"), rs.getBigDecimal("gst"),
                        rs.getBigDecimal("net"), (Long) rs.getObject("adjusts_period_id"), (Long) rs.getObject("fd_entry_id"),
                        (Long) rs.getObject("bond_entry_id"), (Long) rs.getObject("aif_entry_id"), (Long) rs.getObject("loan_entry_id"),
                        (Long) rs.getObject("gift_entry_id"), (Long) rs.getObject("preipo_entry_id"),
                        (Long) rs.getObject("pms_fee_id"), (Long) rs.getObject("nps_entry_id"), (Long) rs.getObject("p2p_entry_id")))
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
        batch("""
                            INSERT INTO brokerage_line (period_id, client_id, product_type, gross, gst, net, upload_id, staged_row_id, adjusts_period_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", adjustments, a -> new Object[]{periodId, a.clientId(), productType, a.gross(), a.gst(), a.net(), uploadId, a.stagedRowId(), adjustsPeriodId});
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
