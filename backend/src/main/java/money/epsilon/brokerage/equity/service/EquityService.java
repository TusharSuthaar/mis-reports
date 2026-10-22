package money.epsilon.brokerage.equity.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equity brokerage (R-89): the broker's monthly "Brokerage Due" report (SMC Global) uploaded on Data uploads. Every trade
 * row is kept with the client found by PAN; a month's later upload replaces the earlier one. When the month is processed,
 * each client's franchise share (Epsilon's part of the gross brokerage) for the month is one EQUITY line.
 */
@Service
public class EquityService {

    static final String AUDIT = "EQUITY_UPLOAD";
    static final String PARTY = "Party";
    static final String PAN = "PAN";
    static final String NAME = "Name";
    static final String DATE = "Date";
    static final String EXCHANGE = "Exchange";
    static final String GROSS = "Gross Recd";
    /** The report has two "Franchise" columns: the code (first) and Epsilon's share (second). */
    static final String SHARE = "Franchise 2";
    static final List<String> REQUIRED = List.of(PARTY, PAN, DATE, GROSS);

    private final JdbcClient jdbc;
    private final PanCipher panCipher;
    private final ClientService clients;
    private final PeriodService periods;
    private final AuditService audit;

    EquityService(JdbcClient jdbc, PanCipher panCipher, ClientService clients, PeriodService periods, AuditService audit) {
        this.jdbc = jdbc;
        this.panCipher = panCipher;
        this.clients = clients;
        this.periods = periods;
        this.audit = audit;
    }

    public record UploadResult(Long uploadId, YearMonth month, String fileName, String broker, int trades, int clients, int clientsFound,
                               int clientsNotFound, BigDecimal grossTotal, BigDecimal shareTotal, Long replacedUploadId) {
    }

    private record Trade(int rowNo, String party, String pan, String name, LocalDate date, String exchange, BigDecimal gross, BigDecimal share) {
    }

    /** Reads the report, checks it is one month, replaces that month's earlier upload and keeps every trade. */
    @Transactional
    public UploadResult upload(byte[] content, String fileName) {
        List<Trade> trades = new ArrayList<>();
        String broker;
        try (ExcelFile file = ExcelFile.open(content)) {
            // The trades may be on any sheet (the broker's file also has a pivot sheet first).
            ExcelFile.Table t = file.sheetNames().stream().map(n -> file.table(n, REQUIRED, 15).orElse(null))
                    .filter(x -> x != null && x.missing().isEmpty()).findFirst()
                    .orElseThrow(() -> new RuleViolationException("This is not the broker's Brokerage Due report: no sheet has the columns "
                            + String.join(", ", REQUIRED)));
            broker = Text.clean(t.title());
            for (ExcelRow r : t.rows()) {
                String pan = PanCipher.normaliseOrNull(r.text(PAN));
                if (pan == null) continue;   // title, sub-header, separator and total lines
                Map<String, String> cells = new HashMap<>();
                r.cells().forEach(c -> cells.put(c.header(), c.text()));
                BigDecimal gross = amount(cells.get(GROSS), r.rowNumber(), GROSS);
                BigDecimal share = amount(cells.get(SHARE), r.rowNumber(), "Franchise share");
                LocalDate date = r.date(DATE);
                trades.add(new Trade(r.rowNumber(), Text.clean(r.text(PARTY)), pan, Text.clean(r.text(NAME)), date, Text.clean(r.text(EXCHANGE)),
                        gross, share));
            }
        }
        if (trades.isEmpty()) throw new RuleViolationException("The report has no trade rows");
        Set<YearMonth> months = trades.stream().map(t -> YearMonth.from(t.date())).collect(Collectors.toCollection(TreeSet::new));
        if (months.size() > 1) throw new RuleViolationException("The report has trades of more than one month: " + months);
        YearMonth month = months.iterator().next();
        periods.list("MF").stream().filter(p -> p.month().equals(month) && p.status() == Period.Status.LOCKED).findFirst().ifPresent(p -> {
            throw new RuleViolationException(month + " is locked: its equity brokerage cannot be replaced");
        });

        Map<String, Long> byPanHash = panIndex();
        Long replaced = jdbc.sql("SELECT id FROM equity_upload WHERE month = ? AND status = 'ACTIVE'").param(month.atDay(1)).query(Long.class).optional().orElse(null);
        if (replaced != null) jdbc.sql("UPDATE equity_upload SET status = 'REPLACED' WHERE id = ?").param(replaced).update();
        BigDecimal gross = trades.stream().map(Trade::gross).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal share = trades.stream().map(Trade::share).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        Set<String> pans = trades.stream().map(Trade::pan).collect(Collectors.toSet());
        long uploadId = jdbc.sql("""
                        INSERT INTO equity_upload (month, file_name, broker, trades, clients, gross_total, share_total, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(month.atDay(1), fileName, broker, trades.size(), pans.size(), gross, share, CurrentUser.username())
                .query(Long.class).single();
        Set<String> found = new java.util.HashSet<>();
        for (Trade t : trades) {
            String hash = panCipher.hash(t.pan());
            Long clientId = byPanHash.get(hash);
            if (clientId != null) found.add(t.pan());
            jdbc.sql("""
                            INSERT INTO equity_trade (upload_id, row_no, party_code, pan_encrypted, pan_hash, client_name, trade_date, exchange, gross,
                                                      share, client_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(uploadId, t.rowNo(), t.party() == null ? "" : t.party(), panCipher.encrypt(t.pan()), hash, t.name(), t.date(), t.exchange(),
                            t.gross(), t.share(), clientId)
                    .update();
        }
        UploadResult result = new UploadResult(uploadId, month, fileName, broker, trades.size(), pans.size(), found.size(), pans.size() - found.size(),
                gross, share, replaced);
        audit.record(AUDIT, uploadId, "UPLOAD", null, result);
        return result;
    }

    private static BigDecimal amount(String text, int row, String what) {
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + row + ": '" + text + "' in " + what + " is not an amount");
        }
    }

    /** PAN hash → client id, by current and former PANs. */
    private Map<String, Long> panIndex() {
        Map<String, Long> byHash = new HashMap<>();
        for (ClientKey k : clients.keys()) if (k.panHash() != null) byHash.putIfAbsent(k.panHash(), k.id());
        clients.formerPans().forEach(byHash::putIfAbsent);
        return byHash;
    }

    // ---- processing ---------------------------------------------------------------------------------------------

    /** One client's equity brokerage of the month for processing: net = the franchise share, no GST. */
    public record EquityForLedger(long uploadId, long clientId, BigDecimal share) {
    }

    /** The month's equity clients and how many of its PANs are not clients yet, without changing anything (R-91). */
    public record MonthCheck(boolean uploaded, List<Long> clientIds, int notInClients) {
    }

    @Transactional(readOnly = true)
    public MonthCheck check(YearMonth month) {
        Long uploadId = jdbc.sql("SELECT id FROM equity_upload WHERE month = ? AND status = 'ACTIVE'").param(month.atDay(1))
                .query(Long.class).optional().orElse(null);
        if (uploadId == null) return new MonthCheck(false, List.of(), 0);
        java.util.Set<Long> ids = new java.util.TreeSet<>(jdbc.sql("SELECT DISTINCT client_id FROM equity_trade WHERE upload_id = ? AND client_id IS NOT NULL")
                .param(uploadId).query(Long.class).list());
        Map<String, Long> byHash = panIndex();
        int missing = 0;
        for (String h : jdbc.sql("SELECT DISTINCT pan_hash FROM equity_trade WHERE upload_id = ? AND client_id IS NULL").param(uploadId).query(String.class).list()) {
            Long id = byHash.get(h);
            if (id == null) missing++;
            else ids.add(id);
        }
        return new MonthCheck(true, List.copyOf(ids), missing);
    }

    /**
     * The month's active upload, one line per client (R-89). A trade whose PAN was not found at upload is looked up again
     * (the client may have been added since). Refused while any PAN is still not a client, so no brokerage is left out.
     */
    @Transactional
    public List<EquityForLedger> forLedger(YearMonth month) {
        Long uploadId = jdbc.sql("SELECT id FROM equity_upload WHERE month = ? AND status = 'ACTIVE'").param(month.atDay(1))
                .query(Long.class).optional().orElse(null);
        if (uploadId == null) return List.of();
        Map<String, Long> byHash = panIndex();
        jdbc.sql("SELECT DISTINCT pan_hash FROM equity_trade WHERE upload_id = ? AND client_id IS NULL").param(uploadId).query(String.class).list()
                .forEach(h -> {
                    Long id = byHash.get(h);
                    if (id != null) jdbc.sql("UPDATE equity_trade SET client_id = ? WHERE upload_id = ? AND pan_hash = ?").params(id, uploadId, h).update();
                });
        List<String> missing = jdbc.sql("SELECT DISTINCT pan_encrypted FROM equity_trade WHERE upload_id = ? AND client_id IS NULL")
                .param(uploadId).query(String.class).list().stream().map(e -> PanCipher.mask(panCipher.decrypt(e))).sorted().distinct().toList();
        if (!missing.isEmpty()) {
            throw new RuleViolationException(missing.size() + " equity client(s) of " + month + " are not in Clients (PAN " + String.join(", ", missing)
                    + "). Add them on the Clients screen, then process.");
        }
        return jdbc.sql("SELECT client_id, sum(share) FROM equity_trade WHERE upload_id = ? GROUP BY client_id ORDER BY client_id").param(uploadId)
                .query((rs, n) -> new EquityForLedger(uploadId, rs.getLong(1), rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP)))
                .list().stream().filter(e -> e.share().signum() != 0).toList();
    }

    // ---- screens ------------------------------------------------------------------------------------------------

    public record UploadView(long id, YearMonth month, String fileName, String broker, int trades, int clients, BigDecimal grossTotal,
                             BigDecimal shareTotal, String status, String uploadedBy, Instant uploadedAt) {
    }

    @Transactional(readOnly = true)
    public List<UploadView> uploads() {
        return jdbc.sql("SELECT * FROM equity_upload ORDER BY month DESC, id DESC").query((rs, n) -> new UploadView(rs.getLong("id"),
                YearMonth.from(rs.getDate("month").toLocalDate()), rs.getString("file_name"), rs.getString("broker"), rs.getInt("trades"), rs.getInt("clients"),
                rs.getBigDecimal("gross_total"), rs.getBigDecimal("share_total"), rs.getString("status"), rs.getString("uploaded_by"),
                rs.getTimestamp("uploaded_at").toInstant())).list();
    }

    public record Filter(YearMonth from, YearMonth to, String q, Long rmId) {
    }

    /** One client in one month, from the month's active upload. {@code clientId} null = the PAN is not a client yet. */
    public record ClientMonth(YearMonth month, Long clientId, String clientName, String pan, String partyCode, String rmName, String branchName,
                              int trades, String exchanges, BigDecimal gross, BigDecimal share, BigDecimal sharePercent) {
    }

    /** The equity tab: every client-month of the active uploads, newest month first; filters optional. */
    @Transactional(readOnly = true)
    public List<ClientMonth> clientMonths(Filter f) {
        return clientMonths(f, null, null);
    }

    /**
     * The client page's holdings (R-09): every month of the active uploads with trades under these PANs (hashes) or
     * already linked to these clients, newest first. A trade whose PAN is not a client yet still shows by its PAN.
     */
    @Transactional(readOnly = true)
    public List<ClientMonth> forPans(Collection<String> panHashes, Collection<Long> clientIds) {
        if (panHashes.isEmpty() && clientIds.isEmpty()) return List.of();
        return clientMonths(new Filter(null, null, null, null), panHashes, clientIds);
    }

    /** {@code panHashes} / {@code clientIds} null = every client. */
    private List<ClientMonth> clientMonths(Filter f, Collection<String> panHashes, Collection<Long> clientIds) {
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        record Agg(LocalDate month, Long clientId, String pan, String name, String party, int trades, String exchanges, BigDecimal gross, BigDecimal share) {
        }
        List<Agg> rows = jdbc.sql("""
                        SELECT u.month, t.client_id, min(t.pan_encrypted) AS pan, min(t.client_name) AS name, min(t.party_code) AS party, count(*) AS trades,
                               string_agg(DISTINCT t.exchange, '+') AS exchanges, sum(t.gross) AS gross, sum(t.share) AS share
                        FROM equity_trade t JOIN equity_upload u ON u.id = t.upload_id
                        WHERE u.status = 'ACTIVE' AND u.month BETWEEN :from AND :to
                          AND (:all OR t.pan_hash IN (:hashes) OR t.client_id IN (:ids))
                        GROUP BY u.month, t.client_id, t.pan_hash ORDER BY u.month DESC, sum(t.share) DESC""")
                .param("from", from).param("to", to).param("all", panHashes == null)
                .param("hashes", panHashes == null || panHashes.isEmpty() ? List.of("-") : List.copyOf(panHashes))
                .param("ids", clientIds == null || clientIds.isEmpty() ? List.of(-1L) : List.copyOf(clientIds))
                .query((rs, n) -> new Agg(rs.getDate("month").toLocalDate(), (Long) rs.getObject("client_id"), rs.getString("pan"), rs.getString("name"),
                        rs.getString("party"), rs.getInt("trades"), rs.getString("exchanges"), rs.getBigDecimal("gross"), rs.getBigDecimal("share")))
                .list();
        Set<Long> ids = rows.stream().map(Agg::clientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = clients.names(ids);
        Map<Long, ClientService.ClientView> views = clients.views(ids);
        boolean fullPan = Roles.canSeeFullPan();
        String q = f.q() == null ? "" : f.q().trim().toUpperCase();
        List<ClientMonth> result = new ArrayList<>();
        for (Agg a : rows) {
            ClientService.ClientView v = a.clientId() == null ? null : views.get(a.clientId());
            if (f.rmId() != null && (v == null || !f.rmId().equals(v.rmId()))) continue;
            String pan = panCipher.decrypt(a.pan());
            String name = a.clientId() == null ? a.name() : names.get(a.clientId());
            if (!q.isEmpty() && !(pan.equals(q) || (name != null && name.toUpperCase().contains(q)) || (a.party() != null && a.party().toUpperCase().contains(q)))) continue;
            BigDecimal pct = a.gross().signum() == 0 ? null : a.share().multiply(new BigDecimal("100")).divide(a.gross(), 1, RoundingMode.HALF_UP);
            result.add(new ClientMonth(YearMonth.from(a.month()), a.clientId(), name, fullPan ? pan : PanCipher.mask(pan), a.party(),
                    v == null ? null : v.rmName(), v == null ? null : v.branchName(), a.trades(), a.exchanges(),
                    a.gross().setScale(2, RoundingMode.HALF_UP), a.share().setScale(2, RoundingMode.HALF_UP), pct));
        }
        return result;
    }

    /** One client's trades in a month (the equity tab's drill-down). */
    public record TradeView(LocalDate date, String exchange, BigDecimal gross, BigDecimal share) {
    }

    @Transactional(readOnly = true)
    public List<TradeView> trades(YearMonth month, String partyCode) {
        return jdbc.sql("""
                        SELECT t.trade_date, t.exchange, t.gross, t.share FROM equity_trade t JOIN equity_upload u ON u.id = t.upload_id
                        WHERE u.status = 'ACTIVE' AND u.month = ? AND t.party_code = ? ORDER BY t.trade_date, t.row_no""")
                .params(month.atDay(1), partyCode)
                .query((rs, n) -> new TradeView(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4))).list();
    }
}
