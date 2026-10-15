package money.epsilon.brokerage.ingestion.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.attribution.service.AttributionService;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.client.service.ClientService.ClientKey;
import money.epsilon.brokerage.ingestion.finesse.FinesseBrokerageParser;
import money.epsilon.brokerage.ingestion.finesse.FinesseClientMasterService;
import money.epsilon.brokerage.ingestion.finesse.FinesseClientMasterService.Code;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Problem;
import money.epsilon.brokerage.ingestion.parser.ParsedIncomeFile.Totals;
import money.epsilon.brokerage.ingestion.parser.StagedIncomeRow;
import money.epsilon.brokerage.ledger.service.LedgerService;
import money.epsilon.brokerage.ledger.service.LedgerService.LineInput;
import money.epsilon.brokerage.period.domain.Period;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.storage.FileStore;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The monthly MF brokerage upload (Finesse): read and check the file (a job), review, then save the month to the
 * ledger. The file gives only client codes and amounts. Each code is matched through the Finesse client master to a
 * PAN, then to that PAN's client in Master MIS (the client with an RM). A client without a PAN of its own is matched by
 * its guardian's PAN, else by its exact name among Master MIS clients that have no PAN either. A code that finds no
 * Master MIS client is kept as a client without an RM and shown as "not in Master MIS": the month can be saved, but
 * not processed until every such client has an RM. Several codes of one person add up into one line.
 */
@Service
public class UploadService {

    static final String AREA = "income-uploads";
    static final String MF = "MF";
    static final String SOURCE = FinesseBrokerageParser.SOURCE;

    /** How a brokerage code found its client. */
    enum MatchKind { PAN, GUARDIAN_PAN, NAME, NOT_IN_MASTER }

    private final FinesseBrokerageParser parser;
    private final FinesseClientMasterService finesseClients;
    private final FileStore files;
    private final PeriodService periods;
    private final ClientService clients;
    private final LedgerService ledger;
    private final AttributionService attribution;
    private final UploadRepository uploads;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final JobService jobs;
    private final ObjectMapper mapper;
    private final PanCipher panCipher;

    UploadService(FinesseBrokerageParser parser, FinesseClientMasterService finesseClients, FileStore files, PeriodService periods,
                  ClientService clients, LedgerService ledger, AttributionService attribution, UploadRepository uploads, JdbcClient jdbc,
                  AuditService audit, JobService jobs, ObjectMapper mapper, PanCipher panCipher) {
        this.parser = parser;
        this.finesseClients = finesseClients;
        this.files = files;
        this.periods = periods;
        this.clients = clients;
        this.ledger = ledger;
        this.attribution = attribution;
        this.uploads = uploads;
        this.jdbc = jdbc;
        this.audit = audit;
        this.jobs = jobs;
        this.mapper = mapper;
        this.panCipher = panCipher;
    }

    // ---- 1. upload ---------------------------------------------------------------------------------------------

    /** Refuses a file uploaded before or a locked month, stores the file and starts reading it. @return the job id */
    @Transactional
    public long start(byte[] content, String fileName, YearMonth month) {
        String sha = sha256(content);
        if (uploads.existsBySha256AndStatusNot(sha, Upload.Status.DISCARDED)) {
            throw new RuleViolationException("This exact file was uploaded before");
        }
        if (finesseClients.status().codes() == 0) {
            throw new RuleViolationException("Upload the Finesse client master first: it says which PAN each client code belongs to");
        }
        PeriodView period = periods.forUpload(MF, month);
        String path = files.save(AREA, content, ".xlsx");
        return jobs.submit(UploadJob.TYPE, mapper.valueToTree(new UploadJob.Payload(path, fileName, sha, period.id())));
    }

    /** {@code notes}: things worth knowing that do not stop the file (late rows, rounding in the Total row). */
    public record StageResult(Long uploadId, List<Problem> errors, int warnings, List<String> notes) {
    }

    /** Used by {@link UploadJob}, inside its transaction: reads the file, matches every code to a client, records every row. */
    StageResult stage(UploadJob.Payload p) {
        ParsedIncomeFile parsed = parser.parse(files.read(p.path()));
        if (!parsed.ok()) return new StageResult(null, parsed.errors(), parsed.warnings().size(), List.of());
        if (uploads.existsBySha256AndStatusNot(p.sha256(), Upload.Status.DISCARDED)) {
            throw new RuleViolationException("This exact file was uploaded before");
        }
        PeriodView period = periods.get(p.periodId());
        List<String> notes = new ArrayList<>(parsed.warnings().stream().map(Problem::message).toList());
        YearMonth biggest = parsed.netByMonth().entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(period.month());
        if (!biggest.equals(period.month())) {
            return new StageResult(null, List.of(new Problem(0, "Most of this file's brokerage is for " + biggest + ", not " + period.month()
                    + ". Upload it against " + biggest + ".")), 0, List.of());
        }
        parsed.netByMonth().forEach((m, net) -> {
            if (!m.equals(period.month())) notes.add("Brokerage for " + m + " (" + net + ") is in this file and is counted in " + period.month());
        });

        Upload upload = uploads.save(new Upload(SOURCE, period.id(), p.fileName(), p.path(), p.sha256(), json(parsed.fileTotals()),
                notes.size(), CurrentUser.username()));
        Matcher matcher = new Matcher(period);
        Map<String, Code> master = finesseClients.find(parsed.rows().stream().map(StagedIncomeRow::clientCode).toList());
        Map<String, Long> linked = clients.idsByExternalCode(SOURCE, parsed.rows().stream().map(StagedIncomeRow::clientCode).toList());
        int created = 0;
        Map<MatchKind, Integer> kinds = new java.util.EnumMap<>(MatchKind.class);
        for (StagedIncomeRow r : parsed.rows()) {
            Match m = matcher.match(master.get(r.clientCode()));
            long clientId;
            boolean isNew = false;
            if (m.clientId() != null) {
                clientId = m.clientId();
            } else if (linked.containsKey(r.clientCode())) {
                clientId = linked.get(r.clientCode());
            } else {
                Code fc = master.get(r.clientCode());
                clientId = clients.createUncoded(fc == null ? r.clientName() : fc.name(), fc == null ? null : fc.pan());
                matcher.remember(fc == null ? null : fc.panHash(), clientId);
                isNew = true;
                created++;
            }
            clients.linkCode(SOURCE, r.clientCode(), clientId);
            kinds.merge(m.kind(), 1, Integer::sum);
            jdbc.sql("""
                            INSERT INTO staged_row (upload_id, row_no, client_code, client_name, gross, gst, net, client_id, new_client,
                                                    match_kind, match_note)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(upload.getId(), r.rowNumber(), r.clientCode(), r.clientName(), r.gross(), r.gst(), r.net(), clientId, isNew,
                            m.kind().name(), m.note())
                    .update();
        }
        audit.record("UPLOAD", upload.getId(), "STAGE", null,
                new StageAudit(p.fileName(), period.month(), parsed.rows().size(), created, kinds, parsed.fileTotals(), parsed.netByMonth()));
        return new StageResult(upload.getId(), List.of(), notes.size(), notes);
    }

    private record Match(Long clientId, MatchKind kind, String note) {
    }

    /** PAN → the Master MIS client (one with an RM on the month's last day), built once per upload. */
    private final class Matcher {

        private final Map<String, List<Long>> byPan = new HashMap<>();
        /** Master MIS clients (with an RM) by name: all of them, and those without a PAN. */
        private final Map<String, List<Long>> inMasterByName = new HashMap<>();
        private final Map<String, List<Long>> inMasterNoPanByName = new HashMap<>();
        private final Map<Long, Long> rmOn;

        Matcher(PeriodView period) {
            List<ClientKey> keys = clients.keys();
            for (ClientKey k : keys) if (k.panHash() != null) byPan.computeIfAbsent(k.panHash(), x -> new ArrayList<>()).add(k.id());
            rmOn = clients.rmsOn(keys.stream().map(ClientKey::id).toList(), period.toDate());
            byPan.values().forEach(ids -> ids.sort(Comparator.comparing((Long id) -> !rmOn.containsKey(id)).thenComparing(id -> id)));
            for (ClientKey k : keys) {
                if (!rmOn.containsKey(k.id())) continue;
                inMasterByName.computeIfAbsent(nameKey(k.nameKey()), x -> new ArrayList<>()).add(k.id());
                if (k.panHash() == null) inMasterNoPanByName.computeIfAbsent(nameKey(k.nameKey()), x -> new ArrayList<>()).add(k.id());
            }
        }

        /**
         * A client with its own PAN is mapped by that PAN only: if it is not in Master MIS the client is highlighted,
         * even when its guardian's PAN or its name is (the reason then says the name matches, so the PAN can be checked).
         * A client without a PAN is mapped by its guardian's PAN, else by its exact name among Master MIS clients that
         * have no PAN (one such client only).
         */
        Match match(Code fc) {
            if (fc == null) return new Match(null, MatchKind.NOT_IN_MASTER, "Code not in the Finesse client master");
            String name = nameKey(fc.name());
            if (fc.panHash() != null) {
                Long own = inMaster(fc.panHash());
                if (own != null) return new Match(own, MatchKind.PAN, null);
                List<Long> known = byPan.getOrDefault(fc.panHash(), List.of());
                String note = inMasterByName.containsKey(name)
                        ? "PAN not in Master MIS; the name matches a Master MIS client with another PAN: check the PAN"
                        : "PAN not in Master MIS";
                return new Match(known.isEmpty() ? null : known.get(0), MatchKind.NOT_IN_MASTER, note);
            }
            Long guardian = inMaster(fc.guardianPanHash());
            if (guardian != null) return new Match(guardian, MatchKind.GUARDIAN_PAN, null);
            List<Long> sameName = inMasterNoPanByName.getOrDefault(name, List.of());
            if (sameName.size() == 1) return new Match(sameName.get(0), MatchKind.NAME, null);
            if (sameName.size() > 1) return new Match(null, MatchKind.NOT_IN_MASTER, "No PAN; " + sameName.size() + " Master MIS clients without a PAN have this name");
            return new Match(null, MatchKind.NOT_IN_MASTER, fc.guardianPanHash() == null ? "No PAN or guardian PAN, and the name is not in Master MIS"
                    : "No PAN; guardian PAN and name not in Master MIS");
        }

        void remember(String panHash, long clientId) {
            if (panHash != null) byPan.computeIfAbsent(panHash, x -> new ArrayList<>()).add(clientId);
        }

        private Long inMaster(String panHash) {
            if (panHash == null) return null;
            return byPan.getOrDefault(panHash, List.of()).stream().filter(rmOn::containsKey).findFirst().orElse(null);
        }
    }

    // ---- 2. review ---------------------------------------------------------------------------------------------

    /** A client of this upload that is not in Master MIS (no RM for the month): Process waits for it. */
    public record NotInMaster(long clientId, String codes, String name, String pan, String reason, BigDecimal net) {
    }

    public record UploadView(long id, String fileName, String status, YearMonth month, long periodId, Totals fileTotals, int rows,
                             int newClients, int warnings, String uploadedBy, Instant uploadedAt, String committedBy, Instant committedAt) {
    }

    /**
     * {@code clients}: lines the month will have (codes of one person added up). {@code correction}: the month is locked,
     * so saving posts the differences into the next open month.
     */
    public record Review(UploadView upload, int codes, int clients, int byPan, int byGuardianPan, int byName, List<NotInMaster> notInMaster,
                         boolean canSave, boolean correction) {
    }

    @Transactional(readOnly = true)
    public Review review(long uploadId) {
        Upload u = find(uploadId);
        PeriodView period = periods.get(u.getPeriodId());
        List<Row> rows = rows(uploadId);
        List<NotInMaster> missing = notInMaster(rows, period);
        int byPan = (int) rows.stream().filter(r -> MatchKind.PAN.name().equals(r.matchKind())).count();
        int byGuardian = (int) rows.stream().filter(r -> MatchKind.GUARDIAN_PAN.name().equals(r.matchKind())).count();
        int byName = (int) rows.stream().filter(r -> MatchKind.NAME.name().equals(r.matchKind())).count();
        int lines = (int) rows.stream().map(Row::clientId).distinct().count();
        return new Review(view(u, period, rows), rows.size(), lines, byPan, byGuardian, byName, missing, u.getStatus() == Upload.Status.STAGED,
                period.status() == Period.Status.LOCKED);
    }

    /** Name match key: upper case, single spaces, dots ignored. */
    static String nameKey(String name) {
        return money.epsilon.brokerage.platform.text.Text.key(name == null ? "" : name.replace('.', ' '));
    }

    /** The upload's clients that have no RM on the month's last day, with why their code found no Master MIS client. */
    @Transactional(readOnly = true)
    public List<NotInMaster> notInMaster(long uploadId) {
        Upload u = find(uploadId);
        return notInMaster(rows(uploadId), periods.get(u.getPeriodId()));
    }

    private List<NotInMaster> notInMaster(List<Row> rows, PeriodView period) {
        Map<Long, Long> rmOn = clients.rmsOn(rows.stream().map(Row::clientId).distinct().toList(), period.toDate());
        Map<Long, List<Row>> byClient = rows.stream().filter(r -> !rmOn.containsKey(r.clientId()))
                .collect(Collectors.groupingBy(Row::clientId, LinkedHashMap::new, Collectors.toList()));
        if (byClient.isEmpty()) return List.of();
        Map<Long, String> pans = jdbc.sql("SELECT id, pan_encrypted FROM client WHERE id IN (:ids) AND pan_encrypted IS NOT NULL")
                .param("ids", byClient.keySet()).query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        boolean fullPan = Roles.canSeeFullPan();
        return byClient.entrySet().stream().map(e -> {
            List<Row> rs = e.getValue();
            String pan = pans.containsKey(e.getKey()) ? panCipher.decrypt(pans.get(e.getKey())) : null;
            String reason = rs.stream().map(Row::matchNote).filter(n -> n != null).findFirst().orElse("No RM in Master MIS for this month");
            return new NotInMaster(e.getKey(), rs.stream().map(Row::code).collect(Collectors.joining(", ")), rs.get(0).name(),
                    fullPan || pan == null ? pan : PanCipher.mask(pan), reason,
                    rs.stream().map(Row::net).reduce(BigDecimal.ZERO, BigDecimal::add));
        }).sorted(Comparator.comparing(NotInMaster::net).reversed()).toList();
    }

    // ---- 3. save the month -------------------------------------------------------------------------------------

    /**
     * {@code lines}: the month's saved lines (or, for a correction, the adjustment lines posted). For a correction
     * {@code adjustmentsPostedTo} is the month that received them.
     */
    public record CommitResult(long uploadId, YearMonth month, LedgerService.Totals lines, Long replacedUploadId,
                               YearMonth adjustmentsPostedTo, int adjustments) {
    }

    /**
     * Writes the month's brokerage lines, one per client (codes of one person added up); a previous saved upload of the
     * same month is replaced (its lines voided) and the month must be processed again. For a LOCKED month nothing in it
     * changes: the difference per client between this file and what the month says is posted as adjustment lines into
     * the next open month.
     */
    @Transactional
    public CommitResult commit(long uploadId) {
        Upload u = find(uploadId);
        if (u.getStatus() != Upload.Status.STAGED) throw new RuleViolationException("This upload is already " + u.getStatus().name().toLowerCase());
        PeriodView period = periods.get(u.getPeriodId());
        if (period.status() == Period.Status.LOCKED) return commitCorrection(u, period);
        Long replaced = null;
        var previous = uploads.findFirstByPeriodIdAndStatus(period.id(), Upload.Status.COMMITTED);
        if (previous.isPresent()) {
            previous.get().supersede();
            ledger.voidActive(period.id(), MF);
            replaced = previous.get().getId();
        }
        List<LineInput> lines = byClient(rows(uploadId)).values().stream()
                .map(r -> new LineInput(r.clientId(), r.id(), r.gross(), r.gst(), r.net())).toList();
        ledger.write(period.id(), MF, uploadId, lines);
        LedgerService.Totals saved = ledger.totals(period.id(), MF);
        Totals file = totals(u.getTotalsJson());
        if (saved.gross().compareTo(file.gross()) != 0 || saved.gst().compareTo(file.gst()) != 0 || saved.net().compareTo(file.net()) != 0) {
            throw new IllegalStateException("Saved lines do not match the file's totals; nothing was saved");
        }
        u.commit(CurrentUser.username());
        uploads.flush();
        attribution.voidSplits(period.id());
        periods.reopen(period.id());
        CommitResult result = new CommitResult(uploadId, period.month(), saved, replaced, null, 0);
        audit.record("UPLOAD", uploadId, "COMMIT", null, result);
        return result;
    }

    private CommitResult commitCorrection(Upload u, PeriodView locked) {
        PeriodView target = periods.nextOpenAfter(MF, locked.month());
        attribution.voidSplits(target.id());
        Map<Long, LedgerService.Amounts> before = ledger.effectiveByClient(locked.id(), MF);
        Map<Long, Row> now = byClient(rows(u.getId()));
        java.util.Set<Long> clientIds = new java.util.TreeSet<>(before.keySet());
        clientIds.addAll(now.keySet());
        List<LedgerService.Adjustment> adjustments = new ArrayList<>();
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal dGross = zero, dGst = zero, dNet = zero;
        for (Long clientId : clientIds) {
            LedgerService.Amounts was = before.getOrDefault(clientId, new LedgerService.Amounts(zero, zero, zero));
            Row r = now.get(clientId);
            BigDecimal gross = (r == null ? zero : r.gross()).subtract(was.gross());
            BigDecimal gst = (r == null ? zero : r.gst()).subtract(was.gst());
            BigDecimal net = (r == null ? zero : r.net()).subtract(was.net());
            if (gross.signum() == 0 && gst.signum() == 0 && net.signum() == 0) continue;
            adjustments.add(new LedgerService.Adjustment(clientId, r == null ? null : r.id(), gross, gst, net));
            dGross = dGross.add(gross);
            dGst = dGst.add(gst);
            dNet = dNet.add(net);
        }
        ledger.writeAdjustments(target.id(), MF, u.getId(), locked.id(), adjustments);
        u.commit(CurrentUser.username());
        u.postedTo(target.id());
        uploads.flush();
        CommitResult result = new CommitResult(u.getId(), locked.month(), new LedgerService.Totals(adjustments.size(), dGross, dGst, dNet),
                null, target.month(), adjustments.size());
        audit.record("UPLOAD", u.getId(), "COMMIT_CORRECTION", null, result);
        return result;
    }

    /** One row per client: the amounts of all its codes added up, carrying the first row's id. */
    private static Map<Long, Row> byClient(List<Row> rows) {
        Map<Long, Row> result = new LinkedHashMap<>();
        for (Row r : rows) result.merge(r.clientId(), r, (a, b) -> a.plus(b));
        return result;
    }

    @Transactional
    public void discard(long uploadId) {
        Upload u = find(uploadId);
        u.discard();
        audit.record("UPLOAD", uploadId, "DISCARD", null, u.getFileName());
    }

    // ---- months overview ---------------------------------------------------------------------------------------

    /** {@code notInMaster}: saved clients of the month with no RM on its last day; Process waits for them. */
    public record MonthView(long periodId, YearMonth month, Period.Status status, LedgerService.Totals saved, UploadView savedUpload,
                            UploadView pendingUpload, String processedBy, Instant processedAt, String lockedBy, Instant lockedAt,
                            long notInMaster) {
    }

    @Transactional(readOnly = true)
    public List<MonthView> months() {
        List<PeriodView> all = periods.list(MF);
        if (all.isEmpty()) return List.of();
        Map<Long, LedgerService.Totals> totals = ledger.totalsByPeriod(MF);
        Map<Long, List<Upload>> byPeriod = uploads.findByPeriodIdInOrderByUploadedAtDesc(all.stream().map(PeriodView::id).toList())
                .stream().collect(Collectors.groupingBy(Upload::getPeriodId));
        Map<Long, Long> missing = jdbc.sql("""
                        SELECT l.period_id, count(DISTINCT l.client_id) AS n
                        FROM brokerage_line l JOIN period p ON p.id = l.period_id
                        WHERE NOT l.voided AND l.product_type = ?
                          AND NOT EXISTS (SELECT 1 FROM client_rm_assignment a WHERE a.client_id = l.client_id
                                          AND a.valid_from <= p.to_date AND (a.valid_to IS NULL OR a.valid_to >= p.to_date))
                        GROUP BY l.period_id""")
                .param(MF).query((rs, n) -> Map.entry(rs.getLong(1), rs.getLong(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return all.stream().map(p -> {
            List<Upload> ups = byPeriod.getOrDefault(p.id(), List.of());
            UploadView saved = ups.stream().filter(x -> x.getStatus() == Upload.Status.COMMITTED).findFirst().map(x -> view(x, p, null)).orElse(null);
            UploadView pending = ups.stream().filter(x -> x.getStatus() == Upload.Status.STAGED).findFirst().map(x -> view(x, p, null)).orElse(null);
            return new MonthView(p.id(), p.month(), p.status(), totals.get(p.id()), saved, pending, p.processedBy(), p.processedAt(),
                    p.lockedBy(), p.lockedAt(), missing.getOrDefault(p.id(), 0L));
        }).toList();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private record Row(long id, int rowNo, String code, String name, BigDecimal gross, BigDecimal gst, BigDecimal net,
                       long clientId, boolean newClient, String matchKind, String matchNote) {

        Row plus(Row o) {
            return new Row(id, rowNo, code, name, gross.add(o.gross), gst.add(o.gst), net.add(o.net), clientId, newClient, matchKind, matchNote);
        }
    }

    private List<Row> rows(long uploadId) {
        return jdbc.sql("""
                        SELECT id, row_no, client_code, client_name, gross, gst, net, client_id, new_client, match_kind, match_note
                        FROM staged_row WHERE upload_id = ? ORDER BY row_no""")
                .param(uploadId)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getInt("row_no"), rs.getString("client_code"), rs.getString("client_name"),
                        rs.getBigDecimal("gross"), rs.getBigDecimal("gst"), rs.getBigDecimal("net"), rs.getLong("client_id"),
                        rs.getBoolean("new_client"), rs.getString("match_kind"), rs.getString("match_note")))
                .list();
    }

    private Upload find(long id) {
        return uploads.findById(id).orElseThrow(() -> new NotFoundException("Upload " + id + " not found"));
    }

    private UploadView view(Upload u, PeriodView p, List<Row> rows) {
        int count = rows != null ? rows.size()
                : jdbc.sql("SELECT count(*) FROM staged_row WHERE upload_id = ?").param(u.getId()).query(Integer.class).single();
        int created = rows != null ? (int) rows.stream().filter(Row::newClient).count()
                : jdbc.sql("SELECT count(*) FROM staged_row WHERE upload_id = ? AND new_client").param(u.getId()).query(Integer.class).single();
        return new UploadView(u.getId(), u.getFileName(), u.getStatus().name(), p.month(), p.id(), totals(u.getTotalsJson()), count, created,
                u.getWarnings(), u.getUploadedBy(), u.getUploadedAt(), u.getCommittedBy(), u.getCommittedAt());
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Totals totals(String json) {
        try {
            return mapper.readValue(json, Totals.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record StageAudit(String fileName, YearMonth month, int codes, int newClients, Map<MatchKind, Integer> matched, Totals totals,
                      Map<YearMonth, BigDecimal> netByMonth) {
    }
}
