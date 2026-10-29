package money.epsilon.brokerage.equity.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
    /** The same report without a PAN column (Sep 2026): "Gross Brokerage (+ Min.Chrg.)", and the client's name second. */
    static final String GROSS_NO_PAN = "Gross Brokerage";
    static final String CLIENT_NAME_NO_PAN = "Name 2";
    static final List<String> REQUIRED_NO_PAN = List.of(PARTY, DATE, GROSS_NO_PAN);

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

    /** {@code byPartyCode} / {@code byName}: clients of a report without PANs, found by party code or by exact name. */
    public record UploadResult(Long uploadId, YearMonth month, String fileName, String broker, int trades, int clients, int clientsFound,
                               int clientsNotFound, BigDecimal grossTotal, BigDecimal shareTotal, Long replacedUploadId, int byPartyCode,
                               int byName) {
    }

    private record Trade(int rowNo, String party, String pan, String name, LocalDate date, String exchange, BigDecimal gross, BigDecimal share) {
    }

    /** Reads the report, checks it is one month, replaces that month's earlier upload and keeps every trade. */
    @Transactional
    public UploadResult upload(byte[] content, String fileName) {
        List<Trade> trades = new ArrayList<>();
        // Party code → PAN from the report's own summary sheet, when the trades sheet has no PAN column.
        Map<String, String> filePans = new HashMap<>();
        String broker;
        try (ExcelFile file = ExcelFile.open(content)) {
            // The trades may be on any sheet (the broker's file also has a pivot sheet first).
            ExcelFile.Table t = table(file, REQUIRED).or(() -> table(file, REQUIRED_NO_PAN))
                    .orElseThrow(() -> new RuleViolationException("This is not the broker's Brokerage Due report: no sheet has the columns "
                            + String.join(", ", REQUIRED) + " (or " + String.join(", ", REQUIRED_NO_PAN) + " without PANs)"));
            broker = Text.clean(t.title());
            boolean withPan = t.has(PAN);
            String grossHeader = withPan ? GROSS : GROSS_NO_PAN;
            if (!withPan) filePans.putAll(partyPansInFile(file, t.sheetName()));
            for (ExcelRow r : t.rows()) {
                String pan = withPan ? PanCipher.normaliseOrNull(r.text(PAN)) : null;
                String party = Text.clean(r.text(PARTY));
                // Title, sub-header, separator and total lines: no PAN (or, without PANs, no party code and trade date).
                if (withPan ? pan == null : party == null || !isDate(r)) continue;
                Map<String, String> cells = new HashMap<>();
                r.cells().forEach(c -> cells.put(c.header(), c.text()));
                BigDecimal gross = amount(cells.get(grossHeader), r.rowNumber(), grossHeader);
                BigDecimal share = amount(cells.get(SHARE), r.rowNumber(), "Franchise share");
                LocalDate date = r.date(DATE);
                String name = withPan ? Text.clean(r.text(NAME)) : Text.clean(cells.get(CLIENT_NAME_NO_PAN));
                trades.add(new Trade(r.rowNumber(), party, pan, name, date, Text.clean(r.text(EXCHANGE)), gross, share));
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
        // A trade without a PAN: its party code's PAN in the report's summary sheet, else from earlier uploads, else the one client
        // with exactly its name.
        Map<String, String> byParty = partyIndex();
        byParty.putAll(filePans);
        Map<String, List<Long>> byName = new HashMap<>();
        for (ClientKey k : clients.keys()) byName.computeIfAbsent(Text.nameKey(k.nameKey()), x -> new ArrayList<>()).add(k.id());
        Map<String, Long> nameClient = new HashMap<>();
        Set<String> viaParty = new java.util.HashSet<>(), viaName = new java.util.HashSet<>();
        List<Trade> resolved = new ArrayList<>();
        for (Trade t : trades) {
            String knownPan = t.pan() == null && t.party() != null ? byParty.get(t.party()) : null;
            List<Long> same = t.pan() != null || knownPan != null || t.name() == null ? List.of() : byName.getOrDefault(Text.nameKey(t.name()), List.of());
            String pan = t.pan();
            if (knownPan != null) {
                viaParty.add(t.party());
                pan = knownPan;
            } else if (same.size() == 1) {
                viaName.add(t.party());
                nameClient.put(t.party(), same.get(0));
                pan = clients.pans(List.of(same.get(0))).get(same.get(0));
            }
            resolved.add(new Trade(t.rowNo(), t.party(), pan, t.name(), t.date(), t.exchange(), t.gross(), t.share()));
        }
        trades = resolved;
        Set<String> pans = trades.stream().map(EquityService::clientKey).collect(Collectors.toSet());
        long uploadId = jdbc.sql("""
                        INSERT INTO equity_upload (month, file_name, broker, trades, clients, gross_total, share_total, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(month.atDay(1), fileName, broker, trades.size(), pans.size(), gross, share, CurrentUser.username())
                .query(Long.class).single();
        Set<String> found = new java.util.HashSet<>();
        for (Trade t : trades) {
            String hash = t.pan() == null ? null : panCipher.hash(t.pan());
            Long clientId = nameClient.containsKey(t.party()) ? nameClient.get(t.party()) : hash == null ? null : byPanHash.get(hash);
            if (clientId != null) found.add(clientKey(t));
            jdbc.sql("""
                            INSERT INTO equity_trade (upload_id, row_no, party_code, pan_encrypted, pan_hash, client_name, trade_date, exchange, gross,
                                                      share, client_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(uploadId, t.rowNo(), t.party() == null ? "" : t.party(), t.pan() == null ? null : panCipher.encrypt(t.pan()), hash, t.name(), t.date(), t.exchange(),
                            t.gross(), t.share(), clientId)
                    .update();
        }
        UploadResult result = new UploadResult(uploadId, month, fileName, broker, trades.size(), pans.size(), found.size(), pans.size() - found.size(),
                gross, share, replaced, viaParty.size(), viaName.size());
        audit.record(AUDIT, uploadId, "UPLOAD", null, result);
        return result;
    }

    private static java.util.Optional<ExcelFile.Table> table(ExcelFile file, List<String> required) {
        return file.sheetNames().stream().map(n -> file.table(n, required, 15).orElse(null)).filter(x -> x != null && x.missing().isEmpty()).findFirst();
    }

    /**
     * The report's other sheets (SMC's pivot: Party, name, total, and the PAN in a column without a header): party code →
     * PAN, for a trades sheet without a PAN column.
     */
    private static Map<String, String> partyPansInFile(ExcelFile file, String tradeSheet) {
        Map<String, String> result = new HashMap<>();
        for (String sheet : file.sheetNames()) {
            if (sheet.equals(tradeSheet)) continue;
            ExcelFile.Table t = file.table(sheet, List.of(PARTY), 15).orElse(null);
            if (t == null || !t.missing().isEmpty()) continue;
            int partyCol = t.columns().indexesOf(PARTY).get(0);
            for (ExcelRow r : t.rows()) {
                Map<Integer, String> cells = file.rowTexts(sheet, r.rowNumber() - 1);
                String party = Text.clean(cells.get(partyCol));
                if (party == null) continue;
                cells.values().stream().map(PanCipher::normaliseOrNull).filter(Objects::nonNull).findFirst()
                        .ifPresent(pan -> result.putIfAbsent(party, pan));
            }
        }
        return result;
    }

    private static boolean isDate(ExcelRow r) {
        try {
            return r.date(DATE) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** One client of the report: its PAN, or (no PAN found) its party code. */
    private static String clientKey(Trade t) {
        return t.pan() != null ? t.pan() : "PARTY:" + t.party();
    }

    /** Party code → its PAN, from the latest earlier trade of that code that had one. */
    private Map<String, String> partyIndex() {
        Map<String, String> m = new HashMap<>();
        jdbc.sql("SELECT DISTINCT ON (party_code) party_code, pan_encrypted FROM equity_trade WHERE party_code <> '' AND pan_encrypted IS NOT NULL"
                        + " ORDER BY party_code, id DESC")
                .query((rs, n) -> m.put(rs.getString(1), panCipher.decrypt(rs.getString(2)))).list();
        return m;
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

    /** The month's equity clients and those of its trades that are not clients yet, without changing anything (R-91). */
    public record MonthCheck(boolean uploaded, List<Long> clientIds, int notInClients, List<Missing> missing) {
        public MonthCheck(boolean uploaded, List<Long> clientIds, int notInClients) {
            this(uploaded, clientIds, notInClients, List.of());
        }
    }

    /** An equity client of the month not found in Clients: as the broker's file has it, with its share of the month. */
    public record Missing(String partyCode, String name, String pan, BigDecimal share) {
    }

    /** Exact client name → the clients with it (a trade without a PAN is found by name only when one client has it). */
    private Map<String, List<Long>> nameIndex() {
        Map<String, List<Long>> byName = new HashMap<>();
        for (ClientKey k : clients.keys()) byName.computeIfAbsent(Text.nameKey(k.nameKey()), x -> new ArrayList<>()).add(k.id());
        return byName;
    }

    /** The client a trade not found at upload finds now: by its PAN, or (no PAN) by its exact name. */
    private static Long lookUp(String panHash, String name, Map<String, Long> byHash, Map<String, List<Long>> byName) {
        if (panHash != null) return byHash.get(panHash);
        List<Long> same = name == null ? List.of() : byName.getOrDefault(Text.nameKey(name), List.of());
        return same.size() == 1 ? same.get(0) : null;
    }

    @Transactional(readOnly = true)
    public MonthCheck check(YearMonth month) {
        Long uploadId = jdbc.sql("SELECT id FROM equity_upload WHERE month = ? AND status = 'ACTIVE'").param(month.atDay(1))
                .query(Long.class).optional().orElse(null);
        if (uploadId == null) return new MonthCheck(false, List.of(), 0);
        java.util.Set<Long> ids = new java.util.TreeSet<>(jdbc.sql("SELECT DISTINCT client_id FROM equity_trade WHERE upload_id = ? AND client_id IS NOT NULL")
                .param(uploadId).query(Long.class).list());
        Map<String, Long> byHash = panIndex();
        Map<String, List<Long>> byName = nameIndex();
        boolean fullPan = Roles.canSeeFullPan();
        // Each client not found at upload (by PAN, or without a PAN by party code), looked up again: it may have been added since.
        List<Missing> missing = new ArrayList<>();
        jdbc.sql("""
                        SELECT pan_hash, min(pan_encrypted) AS pan, min(party_code) AS party, min(client_name) AS name, sum(share) AS share
                        FROM equity_trade WHERE upload_id = ? AND client_id IS NULL
                        GROUP BY pan_hash, CASE WHEN pan_hash IS NULL THEN party_code END ORDER BY sum(share) DESC""")
                .param(uploadId)
                .query((rs, n) -> {
                    Long id = lookUp(rs.getString("pan_hash"), rs.getString("name"), byHash, byName);
                    if (id != null) {
                        ids.add(id);
                    } else {
                        String pan = rs.getString("pan") == null ? null : panCipher.decrypt(rs.getString("pan"));
                        missing.add(new Missing(rs.getString("party"), rs.getString("name"), pan == null ? null : fullPan ? pan : PanCipher.mask(pan),
                                rs.getBigDecimal("share").setScale(2, RoundingMode.HALF_UP)));
                    }
                    return null;
                })
                .list();
        return new MonthCheck(true, List.copyOf(ids), missing.size(), missing);
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
        jdbc.sql("SELECT DISTINCT pan_hash FROM equity_trade WHERE upload_id = ? AND client_id IS NULL AND pan_hash IS NOT NULL").param(uploadId)
                .query(String.class).list()
                .forEach(h -> {
                    Long id = byHash.get(h);
                    if (id != null) jdbc.sql("UPDATE equity_trade SET client_id = ? WHERE upload_id = ? AND pan_hash = ?").params(id, uploadId, h).update();
                });
        // Trades without a PAN: by exact name now (the client may have been added since the upload).
        Map<String, List<Long>> byName = nameIndex();
        jdbc.sql("SELECT party_code, min(client_name) FROM equity_trade WHERE upload_id = ? AND client_id IS NULL AND pan_hash IS NULL GROUP BY party_code")
                .param(uploadId).query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list()
                .forEach(p -> {
                    Long id = lookUp(null, p[1], byHash, byName);
                    if (id != null) {
                        jdbc.sql("UPDATE equity_trade SET client_id = ? WHERE upload_id = ? AND party_code = ? AND pan_hash IS NULL AND client_id IS NULL")
                                .params(id, uploadId, p[0]).update();
                    }
                });
        List<String> missing = jdbc.sql("SELECT DISTINCT pan_encrypted, party_code FROM equity_trade WHERE upload_id = ? AND client_id IS NULL")
                .param(uploadId)
                .query((rs, n) -> rs.getString(1) == null ? "party " + rs.getString(2) : "PAN " + PanCipher.mask(panCipher.decrypt(rs.getString(1))))
                .list().stream().sorted().distinct().toList();
        if (!missing.isEmpty()) {
            throw new RuleViolationException(missing.size() + " equity client(s) of " + month + " are not in Clients (" + String.join(", ", missing)
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
        LocalDate from = f.from() == null ? LocalDate.of(2000, 1, 1) : f.from().atDay(1);
        LocalDate to = f.to() == null ? LocalDate.of(2999, 12, 1) : f.to().atDay(1);
        record Agg(LocalDate month, Long clientId, String pan, String name, String party, int trades, String exchanges, BigDecimal gross, BigDecimal share) {
        }
        List<Agg> rows = jdbc.sql("""
                        SELECT u.month, t.client_id, min(t.pan_encrypted) AS pan, min(t.client_name) AS name, min(t.party_code) AS party, count(*) AS trades,
                               string_agg(DISTINCT t.exchange, '+') AS exchanges, sum(t.gross) AS gross, sum(t.share) AS share
                        FROM equity_trade t JOIN equity_upload u ON u.id = t.upload_id
                        WHERE u.status = 'ACTIVE' AND u.month BETWEEN ? AND ?
                        GROUP BY u.month, t.client_id, t.pan_hash, CASE WHEN t.pan_hash IS NULL THEN t.party_code END
                        ORDER BY u.month DESC, sum(t.share) DESC""")
                .params(from, to)
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
            String pan = a.pan() == null ? null : panCipher.decrypt(a.pan());
            String name = a.clientId() == null ? a.name() : names.get(a.clientId());
            if (!q.isEmpty() && !(q.equals(pan) || (name != null && name.toUpperCase().contains(q)) || (a.party() != null && a.party().toUpperCase().contains(q)))) continue;
            BigDecimal pct = a.gross().signum() == 0 ? null : a.share().multiply(new BigDecimal("100")).divide(a.gross(), 1, RoundingMode.HALF_UP);
            result.add(new ClientMonth(YearMonth.from(a.month()), a.clientId(), name, pan == null ? null : fullPan ? pan : PanCipher.mask(pan), a.party(),
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
