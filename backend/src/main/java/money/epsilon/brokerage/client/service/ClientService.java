package money.epsilon.brokerage.client.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.client.domain.Client;
import money.epsilon.brokerage.client.domain.ClientExternalId;
import money.epsilon.brokerage.client.domain.ClientRmAssignment;
import money.epsilon.brokerage.client.repository.ClientExternalIdRepository;
import money.epsilon.brokerage.client.repository.ClientRepository;
import money.epsilon.brokerage.client.repository.ClientRmAssignmentRepository;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.EmployeeExited;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.PageResponse;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Clients, their codes in source systems, and their RM over time. */
@Service
public class ClientService {

    public static final String INVESTWELL = "INVESTWELL";
    static final String AUDIT_CLIENT_RM = "CLIENT_RM";

    private final ClientRepository clients;
    private final ClientExternalIdRepository externalIds;
    private final ClientRmAssignmentRepository assignments;
    private final EmployeeService employees;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final ChangeWindow window;
    private final JdbcClient jdbc;
    private final org.springframework.context.ApplicationEventPublisher events;

    ClientService(ClientRepository clients, ClientExternalIdRepository externalIds, ClientRmAssignmentRepository assignments,
                  EmployeeService employees, PanCipher panCipher, AuditService audit, JdbcClient jdbc, ChangeWindow window,
                  org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.clients = clients;
        this.externalIds = externalIds;
        this.assignments = assignments;
        this.employees = employees;
        this.panCipher = panCipher;
        this.audit = audit;
        this.jdbc = jdbc;
        this.window = window;
    }

    // ---- reading -------------------------------------------------------------------------------------------

    /**
     * Page of clients sorted by name. {@code q} matches the name, any source code (Finesse shown first) or, when it is a
     * full PAN, the PAN. {@code needsRm} = no current RM.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClientView> page(String q, boolean needsRm, Long rmId, int page, int size) {
        return page(q, needsRm, rmId, null, page, size);
    }

    /** {@code attention}: null, "SHARED_PAN" (a PAN on more than one client record) or "NO_PAN". */
    @Transactional(readOnly = true)
    public PageResponse<ClientView> page(String q, boolean needsRm, Long rmId, String attention, int page, int size) {
        String only = attention == null ? "" : attention;
        if (!only.isEmpty() && !only.equals("SHARED_PAN") && !only.equals("NO_PAN")) throw new IllegalArgumentException("Unknown list: " + attention);
        String query = Text.clean(q) == null ? "" : Text.clean(q).toLowerCase();
        String panHash = "";
        try {
            String pan = PanCipher.normalise(query);
            if (pan != null) panHash = panCipher.hash(pan);
        } catch (IllegalArgumentException notAPan) {
            // a name or a code
        }
        List<Row> rows = jdbc.sql("""
                        SELECT c.id, c.name, c.pan_encrypted, x.code, a.rm_id, count(*) OVER () AS total
                        FROM client c
                        LEFT JOIN LATERAL (SELECT code FROM client_external_id e WHERE e.client_id = c.id
                                           ORDER BY e.source = :source DESC, e.code LIMIT 1) x ON true
                        LEFT JOIN client_rm_assignment a ON a.client_id = c.id AND a.valid_to IS NULL
                        WHERE (:q = '' OR lower(c.name) LIKE :like OR c.pan_hash = :panHash
                               OR EXISTS (SELECT 1 FROM client_external_id e WHERE e.client_id = c.id AND lower(e.code) LIKE :like))
                          AND (:needsRm = false OR a.id IS NULL)
                          AND (:rmId = 0 OR a.rm_id = :rmId)
                          AND (:only <> 'NO_PAN' OR c.pan_hash IS NULL)
                          AND (:only <> 'SHARED_PAN' OR c.pan_hash IN (SELECT pan_hash FROM client WHERE pan_hash IS NOT NULL
                                                                       GROUP BY pan_hash HAVING count(*) > 1))
                        ORDER BY (CASE WHEN :only = 'SHARED_PAN' THEN c.pan_hash END), lower(c.name), c.id
                        LIMIT :size OFFSET :offset""")
                .param("source", "FINESSE").param("q", query).param("panHash", panHash).param("like", "%" + query + "%")
                .param("needsRm", needsRm).param("rmId", rmId == null ? 0L : rmId).param("only", only)
                .param("size", size).param("offset", (long) page * size)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("name"), rs.getString("pan_encrypted"), rs.getString("code"),
                        (Long) rs.getObject("rm_id"), rs.getLong("total")))
                .list();
        Map<Long, EmployeeView> rms = employees.list().stream().collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        boolean fullPan = Roles.canSeeFullPan();
        List<ClientView> items = rows.stream().map(r -> view(r.id, r.name, r.panEncrypted, r.code, r.rmId, rms, fullPan)).toList();
        return new PageResponse<>(items, page, size, rows.isEmpty() ? 0 : rows.get(0).total);
    }

    @Transactional(readOnly = true)
    public ClientDetail get(long id) {
        Client c = find(id);
        Optional<ClientExternalId> ext = externalIds.findByClientIdIn(List.of(id)).stream().filter(x -> x.getSource().equals(INVESTWELL)).findFirst();
        Long rmId = assignments.findFirstByClientIdOrderByValidFromDesc(id).filter(a -> a.getValidTo() == null)
                .map(ClientRmAssignment::getRmId).orElse(null);
        Map<Long, EmployeeView> rms = employees.list().stream().collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        String familyHead = c.getFamilyHeadClientId() == null ? null : clients.findById(c.getFamilyHeadClientId()).map(Client::getName).orElse(null);
        return new ClientDetail(view(id, c.getName(), c.getPanEncrypted(), ext.map(ClientExternalId::getCode).orElse(null), rmId, rms, Roles.canSeeFullPan()),
                ext.map(ClientExternalId::getCode2).orElse(null), c.getFamilyHeadCode(), familyHead);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> rmHistory(long clientId) {
        find(clientId);
        Map<Long, EmployeeView> rms = employees.list().stream().collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        return assignments.findByClientIdOrderByValidFromDesc(clientId).stream()
                .map(a -> HistoryEntry.of(a, a.getRmId(), rmLabel(rms.get(a.getRmId()))))
                .toList();
    }

    /** What other lists can find this client by: its PAN, its former PANs (as hashes) and its codes per source. */
    public record Identity(long id, String panHash, List<String> formerPanHashes, Map<String, List<String>> codes) {
    }

    @Transactional(readOnly = true)
    public Identity identity(long id) {
        Client c = find(id);
        List<String> former = jdbc.sql("SELECT pan_hash FROM client_former_pan WHERE client_id = ?").param(id).query(String.class).list();
        Map<String, List<String>> codes = new HashMap<>();
        for (ClientExternalId x : externalIds.findByClientIdIn(List.of(id))) {
            codes.computeIfAbsent(x.getSource(), k -> new java.util.ArrayList<>()).add(x.getCode());
            if (x.getCode2() != null) codes.get(x.getSource()).add(x.getCode2());
        }
        return new Identity(id, c.getPanHash(), former, codes);
    }

    /**
     * client id → its client code (the Investwell code, as {@link #get} shows it), for many clients in one query: lists use
     * this instead of {@link #get} per row.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> codes(Collection<Long> clientIds) {
        Map<Long, String> result = new HashMap<>();
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            jdbc.sql("SELECT client_id, min(code) FROM client_external_id WHERE source = :source AND client_id IN (:ids) GROUP BY client_id")
                    .param("source", INVESTWELL).param("ids", chunk)
                    .query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list().forEach(e -> result.put(e.getKey(), e.getValue()));
        }
        return result;
    }

    /** client id → how the client looks today (name, PAN as the user may see it, code, RM, branch), for many clients at once. */
    @Transactional(readOnly = true)
    public Map<Long, ClientView> views(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        Map<Long, EmployeeView> rms = employees.list().stream().collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        Map<Long, String> codes = codes(clientIds);
        Map<Long, Long> rmOf = currentRms(clientIds);
        boolean fullPan = Roles.canSeeFullPan();
        Map<Long, ClientView> result = new HashMap<>();
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            jdbc.sql("SELECT id, name, pan_encrypted FROM client WHERE id IN (:ids)").param("ids", chunk)
                    .query((rs, n) -> view(rs.getLong("id"), rs.getString("name"), rs.getString("pan_encrypted"), codes.get(rs.getLong("id")),
                            rmOf.get(rs.getLong("id")), rms, fullPan))
                    .list().forEach(v -> result.put(v.id(), v));
        }
        return result;
    }

    /** source code → client id, for the codes that exist. */
    @Transactional(readOnly = true)
    public Map<String, Long> idsByExternalCode(String source, Collection<String> codes) {
        Map<String, Long> result = new HashMap<>();
        for (List<String> chunk : chunks(List.copyOf(codes), 1000)) {
            externalIds.findBySourceAndCodeIn(source, chunk).forEach(x -> result.put(x.getCode(), x.getClientId()));
        }
        return result;
    }

    /** client id → current RM id, for those that have one. */
    @Transactional(readOnly = true)
    public Map<Long, Long> currentRms(Collection<Long> clientIds) {
        Map<Long, Long> result = new HashMap<>();
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            assignments.findByClientIdInAndValidToIsNull(chunk).forEach(a -> result.put(a.getClientId(), a.getRmId()));
        }
        return result;
    }

    /**
     * For matching outside lists by PAN: {@link #keys()} plus one key per former PAN (corrected on the Clients screen)
     * that no client holds now, pointing at the client it was corrected on. Do not use for per-client work (ids repeat).
     */
    @Transactional(readOnly = true)
    public List<ClientKey> matchKeys() {
        List<ClientKey> all = new ArrayList<>(keys());
        formerPanKeys().forEach(all::add);
        return all;
    }

    /** Former PAN hash → its client, for PANs no client holds now. */
    @Transactional(readOnly = true)
    public Map<String, Long> formerPans() {
        Map<String, Long> result = new HashMap<>();
        formerPanKeys().forEach(k -> result.put(k.panHash(), k.id()));
        return result;
    }

    private List<ClientKey> formerPanKeys() {
        return jdbc.sql("""
                        SELECT f.client_id, c.name, f.pan_hash FROM client_former_pan f JOIN client c ON c.id = f.client_id
                        WHERE NOT EXISTS (SELECT 1 FROM client x WHERE x.pan_hash = f.pan_hash)""")
                .query((rs, n) -> new ClientKey(rs.getLong("client_id"), Text.key(rs.getString("name")), rs.getString("pan_hash")))
                .list();
    }

    /** Every client's id, name match key and PAN hash: for matching rows of outside lists by PAN + name. */
    @Transactional(readOnly = true)
    public List<ClientKey> keys() {
        return jdbc.sql("SELECT id, name, pan_hash FROM client")
                .query((rs, n) -> new ClientKey(rs.getLong("id"), Text.key(rs.getString("name")), rs.getString("pan_hash")))
                .list();
    }

    @Transactional(readOnly = true)
    public void requireExists(long id) {
        find(id);
    }

    /** client id → its RM on {@code day}, for those that had one. */
    @Transactional(readOnly = true)
    public Map<Long, Long> rmsOn(Collection<Long> clientIds, LocalDate day) {
        Map<Long, Long> result = new HashMap<>();
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            assignments.findAllOn(chunk, day).forEach(a -> result.put(a.getClientId(), a.getRmId()));
        }
        return result;
    }

    // ---- changing ------------------------------------------------------------------------------------------

    /** Creates a client known by {@code code} in {@code source}. An invalid PAN is refused. */
    @Transactional
    public long create(String source, String code, String code2, String name, String pan, String familyHeadCode) {
        String key = Text.required(code, "Client code");
        if (externalIds.findBySourceAndCode(source, key).isPresent()) {
            throw new RuleViolationException("A client with " + source + " code " + key + " already exists");
        }
        String normalisedPan = PanCipher.normalise(pan);
        Client c = clients.save(new Client(Text.required(name, "Client name"),
                normalisedPan == null ? null : panCipher.encrypt(normalisedPan),
                normalisedPan == null ? null : panCipher.hash(normalisedPan),
                Text.clean(familyHeadCode)));
        externalIds.save(new ClientExternalId(c.getId(), source, key, Text.clean(code2)));
        return c.getId();
    }

    /**
     * A client with no source code yet (Finesse brokerage whose PAN is not in Master MIS): created without an RM, so it
     * shows as "not in master" until it gets one. @return the new client's id
     */
    @Transactional
    public long createUncoded(String name, String pan) {
        String normalisedPan = pan == null ? null : PanCipher.normalise(pan);
        Client c = clients.save(new Client(Text.required(name, "Client name"),
                normalisedPan == null ? null : panCipher.encrypt(normalisedPan),
                normalisedPan == null ? null : panCipher.hash(normalisedPan), null));
        return c.getId();
    }

    /** RM id → how many clients have that RM today. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countByRm() {
        Map<Long, Long> result = new HashMap<>();
        jdbc.sql("SELECT rm_id, count(*) FROM client_rm_assignment WHERE valid_to IS NULL GROUP BY rm_id")
                .query((rs, n) -> Map.entry(rs.getLong(1), rs.getLong(2))).list().forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /** How many clients each "needs attention" list has. */
    @Transactional(readOnly = true)
    public Attention attention() {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM client WHERE pan_hash IN (SELECT pan_hash FROM client WHERE pan_hash IS NOT NULL
                                                                              GROUP BY pan_hash HAVING count(*) > 1)) AS shared_pan,
                               (SELECT count(*) FROM client c WHERE NOT EXISTS
                                   (SELECT 1 FROM client_rm_assignment a WHERE a.client_id = c.id AND a.valid_to IS NULL)) AS no_rm,
                               (SELECT count(*) FROM client WHERE pan_hash IS NULL) AS no_pan""")
                .query((rs, n) -> new Attention(rs.getLong("shared_pan"), rs.getLong("no_rm"), rs.getLong("no_pan"))).single();
    }

    public record Attention(long sharedPan, long noRm, long noPan) {
    }

    /**
     * A client added by hand (Client master screen): name, optional PAN (refused if another client has it), and its RM
     * from a date. Audited. @return the new client's id
     */
    @Transactional
    public long addByHand(String name, String pan, long rmId, LocalDate from, String reason) {
        String normalised = pan == null || pan.isBlank() ? null : PanCipher.normalise(pan);
        if (normalised != null) {
            List<String> others = jdbc.sql("SELECT name FROM client WHERE pan_hash = ?").param(panCipher.hash(normalised)).query(String.class).list();
            if (!others.isEmpty()) {
                throw new RuleViolationException("PAN " + PanCipher.mask(normalised) + " is already on " + String.join(", ", others)
                        + ": open that client instead");
            }
        }
        long id = createUncoded(name, normalised);
        audit.record("CLIENT", id, "CREATE", null, new PanAudit(normalised == null ? null : PanCipher.mask(normalised),
                "Added on the Clients screen" + (reason == null || reason.isBlank() ? "" : ": " + Text.clean(reason))));
        setRm(id, rmId, from, reason == null || reason.isBlank() ? "Added on the Clients screen" : Text.clean(reason));
        return id;
    }

    /** The client record's own audited changes (added, PAN corrections), newest first. */
    @Transactional(readOnly = true)
    public List<AuditService.AuditView> changes(long clientId) {
        find(clientId);
        return audit.search(new AuditService.AuditFilter("CLIENT", String.valueOf(clientId), null, null, null, null), 0, 100).items();
    }

    /** Points a source code at a client (adds the link, or moves it when the code now belongs to another client). */
    @Transactional
    public void linkCode(String source, String code, long clientId) {
        jdbc.sql("""
                        INSERT INTO client_external_id (client_id, source, code) VALUES (?, ?, ?)
                        ON CONFLICT (source, code) DO UPDATE SET client_id = excluded.client_id""")
                .params(clientId, source, code).update();
    }

    /** Links each client to its family head where the head's code (same source) now exists. Returns how many. */
    @Transactional
    public int resolveFamilyHeads(String source) {
        clients.flush();
        return jdbc.sql("""
                        UPDATE client c SET family_head_client_id = x.client_id
                        FROM client_external_id x
                        WHERE x.source = :source AND x.code = c.family_head_code
                          AND c.family_head_code IS NOT NULL AND c.family_head_client_id IS NULL""")
                .param("source", source).update();
    }

    /**
     * The client's RM is {@code rmId} from {@code from}: the previous RM ends the day before (history is kept), or, from
     * the current entry's own first day, that entry is corrected. Refused if the client already has this RM.
     */
    @Transactional
    public void changeRm(long clientId, long rmId, LocalDate from, String reason) {
        Client c = find(clientId);
        if (setRm(clientId, rmId, from, reason) == DatedHistory.Outcome.UNCHANGED) {
            throw new RuleViolationException(c.getName() + " is already with " + employees.get(rmId).name());
        }
    }

    private DatedHistory.Outcome setRm(long clientId, long rmId, LocalDate from, String reason) {
        window.beforeChange(from);
        employees.requireEmployedOn(rmId, from);
        return DatedHistory.setFrom(assignments.findFirstByClientIdOrderByValidFromDesc(clientId), from, a -> a.getRmId().equals(rmId),
                a -> a.correctRm(rmId, reason), () -> new ClientRmAssignment(clientId, rmId, from, reason),
                assignments, audit, AUDIT_CLIENT_RM, this::auditView, reason);
    }

    /**
     * The client's RM is {@code rmId} from {@code from}, as a master file says. Same RM: nothing. An entry that starts on
     * {@code from} is corrected in place (it was wrong from its first day); otherwise this is an ordinary change.
     */
    @Transactional
    public void setRmFrom(long clientId, long rmId, LocalDate from, String reason) {
        find(clientId);
        setRm(clientId, rmId, from, reason);
    }

    /** client id → its current (open) RM entry: RM and the day it started. */
    @Transactional(readOnly = true)
    public Map<Long, CurrentRm> currentRmEntries(Collection<Long> clientIds) {
        Map<Long, CurrentRm> result = new HashMap<>();
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            assignments.findByClientIdInAndValidToIsNull(chunk).forEach(a -> result.put(a.getClientId(), new CurrentRm(a.getRmId(), a.getValidFrom())));
        }
        return result;
    }

    public record CurrentRm(long rmId, LocalDate since) {
    }

    /**
     * Corrects a client's PAN (e.g. a typo in the master). Refused when another client already has that PAN. The next
     * brokerage upload / Master MIS load matches the client by the new PAN.
     */
    @Transactional
    public void changePan(long clientId, String pan, String reason) {
        changePan(clientId, pan, reason, false);
    }

    /**
     * Corrects the client's PAN. With {@code takeFromOther}, a PAN that sits on another client record without an RM
     * (a duplicate a file created before the PAN was fixed) is moved here: the other record keeps no PAN. Both audited.
     */
    @Transactional
    public void changePan(long clientId, String pan, String reason, boolean takeFromOther) {
        Client c = find(clientId);
        String normalised = PanCipher.normalise(pan);
        if (normalised == null) throw new IllegalArgumentException("Enter the PAN");
        String hash = panCipher.hash(normalised);
        if (hash.equals(c.getPanHash())) throw new RuleViolationException(c.getName() + " already has this PAN");
        List<Long> others = jdbc.sql("SELECT id FROM client WHERE pan_hash = ? AND id <> ?").params(hash, clientId).query(Long.class).list();
        if (!others.isEmpty()) {
            Client other = find(others.get(0));
            if (!takeFromOther) {
                throw new RuleViolationException("PAN " + PanCipher.mask(normalised) + " is already on " + other.getName()
                        + " (client " + other.getId() + ")");
            }
            if (!assignments.findByClientIdOrderByValidFromDesc(other.getId()).isEmpty()) {
                throw new RuleViolationException(other.getName() + " (client " + other.getId() + ") has an RM, so it is a real client: "
                        + "correct the PAN in Master MIS instead");
            }
            other.changePan(null, null);
            clients.saveAndFlush(other);
            audit.record("CLIENT", other.getId(), "PAN_CHANGE", new PanAudit(PanCipher.mask(normalised), null),
                    new PanAudit(null, "PAN moved to client " + clientId + (reason == null ? "" : ": " + Text.clean(reason))));
        }
        String before = c.getPanEncrypted() == null ? null : PanCipher.mask(panCipher.decrypt(c.getPanEncrypted()));
        if (c.getPanHash() != null) {
            // Later files with the old PAN still find this client (R-06).
            jdbc.sql("INSERT INTO client_former_pan (pan_hash, client_id, changed_by) VALUES (?, ?, ?) ON CONFLICT (pan_hash) DO UPDATE SET client_id = excluded.client_id")
                    .params(c.getPanHash(), clientId, money.epsilon.brokerage.platform.security.CurrentUser.username()).update();
        }
        c.changePan(panCipher.encrypt(normalised), hash);
        clients.saveAndFlush(c);
        audit.record("CLIENT", clientId, "PAN_CHANGE", new PanAudit(before, null), new PanAudit(PanCipher.mask(normalised), Text.clean(reason)));
    }

    /**
     * The client's PAN is really its guardian's (a minor), and another client — the guardian — has that PAN: the PAN
     * is removed from this record, so the minor's codes map to the guardian by guardian PAN (R-03). Audited.
     */
    @Transactional
    public void markGuardianPan(long clientId, String reason) {
        Client c = find(clientId);
        if (c.getPanHash() == null) throw new RuleViolationException(c.getName() + " has no PAN");
        List<String> guardians = jdbc.sql("SELECT name FROM client WHERE pan_hash = ? AND id <> ?").params(c.getPanHash(), clientId)
                .query(String.class).list();
        if (guardians.isEmpty()) {
            throw new RuleViolationException("No other client has this PAN, so it is " + c.getName() + "'s own: correct it instead");
        }
        String before = PanCipher.mask(panCipher.decrypt(c.getPanEncrypted()));
        c.changePan(null, null);
        clients.saveAndFlush(c);
        audit.record("CLIENT", clientId, "PAN_CHANGE", new PanAudit(before, null),
                new PanAudit(null, "Guardian's PAN (on " + String.join(", ", guardians) + ")" + (reason == null ? "" : ": " + Text.clean(reason))));
    }

    record PanAudit(String maskedPan, String reason) {
    }

    /** client id → PAN (plain, normalised) for the clients that have one. Callers mask it for users who may not see it. */
    @Transactional(readOnly = true)
    public Map<Long, String> pans(Collection<Long> clientIds) {
        Map<Long, String> result = new HashMap<>();
        if (clientIds.isEmpty()) return result;
        for (List<Long> chunk : chunks(List.copyOf(clientIds), 1000)) {
            jdbc.sql("SELECT id, pan_encrypted FROM client WHERE id IN (:ids) AND pan_encrypted IS NOT NULL").param("ids", chunk)
                    .query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                    .forEach(e -> result.put(e.getKey(), panCipher.decrypt(e.getValue())));
        }
        return result;
    }

    /** Clients whose RM changed on a day inside {@code from}..{@code to} (more than one RM entry overlaps it). */
    @Transactional(readOnly = true)
    public java.util.Set<Long> clientsWithRmChangeIn(LocalDate from, LocalDate to) {
        return java.util.Set.copyOf(jdbc.sql("""
                        SELECT client_id FROM client_rm_assignment
                        WHERE valid_from <= ? AND (valid_to IS NULL OR valid_to >= ?)
                        GROUP BY client_id HAVING count(*) > 1""")
                .params(to, from).query(Long.class).list());
    }

    /** client id → name. */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> clientIds) {
        Map<Long, String> result = new HashMap<>();
        if (clientIds.isEmpty()) return result;
        jdbc.sql("SELECT id, name FROM client WHERE id IN (:ids)").param("ids", clientIds)
                .query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list().forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /**
     * An RM who leaves with a replacement: their clients are with the replacement from the next day (R-61). Without a
     * replacement the clients stay as they are, to be moved by hand.
     */
    @EventListener
    public void onEmployeeExited(EmployeeExited event) {
        if (event.replacedBy() == null) return;
        if (!assignments.findByRmIdAndValidFromAfter(event.employeeId(), event.lastDay()).isEmpty()) {
            throw new RuleViolationException(event.employeeName() + " has a client from after the last working day");
        }
        LocalDate from = event.lastDay().plusDays(1);
        String reason = event.reason() == null ? "Took over from " + event.employeeName() : event.reason();
        for (ClientRmAssignment a : assignments.findByRmIdAndValidToIsNull(event.employeeId())) {
            setRm(a.getClientId(), event.replacedBy(), from, reason);
        }
    }

    /** {@link #changeRm} for several clients at once: all of them or none. */
    @Transactional
    public int changeRmForAll(Collection<Long> clientIds, long rmId, LocalDate from, String reason) {
        if (clientIds.isEmpty()) throw new IllegalArgumentException("Choose at least one client");
        for (Long id : clientIds) changeRm(id, rmId, from, reason);
        return clientIds.size();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private Client find(long id) {
        return clients.findById(id).orElseThrow(() -> new NotFoundException("Client " + id + " not found"));
    }

    private ClientView view(long id, String name, String panEncrypted, String code, Long rmId, Map<Long, EmployeeView> rms, boolean fullPan) {
        String pan = panEncrypted == null ? null : panCipher.decrypt(panEncrypted);
        EmployeeView rm = rmId == null ? null : rms.get(rmId);
        return new ClientView(id, name, fullPan ? pan : PanCipher.mask(pan), code, rmId, rm == null ? null : rm.name(),
                rm == null ? null : rm.branchName());
    }

    private static String rmLabel(EmployeeView rm) {
        if (rm == null) return "?";
        return rm.branchName() == null ? rm.name() : rm.name() + " (" + rm.branchName() + ")";
    }

    /**
     * Two records of one person: {@code sourceId} is merged into {@code targetId} and removed (R-07). Its codes, staged rows
     * and former PANs (and its partners, when the target has none) move to the target; its RM history goes, the target
     * keeps its own. {@code keepSourcePan}: the target takes the source's PAN. Either way the PAN not kept becomes a former
     * PAN of the target, so files that still carry it find the target (R-06). Audited.
     */
    @Transactional
    public void merge(long sourceId, long targetId, boolean keepSourcePan, String reason) {
        if (sourceId == targetId) throw new RuleViolationException("Pick another client to merge into");
        Client source = find(sourceId);
        Client target = find(targetId);
        String why = Text.clean(reason);
        MergeAudit before = new MergeAudit(source.getName(), maskedPan(source), target.getName(), maskedPan(target),
                externalIds.findByClientIdIn(List.of(sourceId)).stream().map(x -> x.getSource() + " " + x.getCode()).toList(),
                assignments.findByClientIdOrderByValidFromDesc(sourceId).stream().map(this::auditView).toList(), why);

        events.publishEvent(new ClientMerged(sourceId, source.getName(), targetId, target.getName(), why));
        jdbc.sql("UPDATE client_external_id SET client_id = ? WHERE client_id = ?").params(targetId, sourceId).update();
        jdbc.sql("UPDATE client SET family_head_client_id = ? WHERE family_head_client_id = ?").params(targetId, sourceId).update();
        jdbc.sql("UPDATE client_former_pan SET client_id = ? WHERE client_id = ?").params(targetId, sourceId).update();

        String sourceEnc = source.getPanEncrypted();
        String sourceHash = source.getPanHash();
        boolean takeSourcePan = keepSourcePan && sourceHash != null;
        String notKept = takeSourcePan ? target.getPanHash() : sourceHash;
        source.changePan(null, null);
        clients.saveAndFlush(source);
        if (takeSourcePan) {
            target.changePan(sourceEnc, sourceHash);
            clients.saveAndFlush(target);
        }
        if (notKept != null && !notKept.equals(target.getPanHash())) {
            jdbc.sql("""
                            INSERT INTO client_former_pan (pan_hash, client_id, changed_by) VALUES (?, ?, ?)
                            ON CONFLICT (pan_hash) DO UPDATE SET client_id = excluded.client_id""")
                    .params(notKept, targetId, money.epsilon.brokerage.platform.security.CurrentUser.username()).update();
        }
        assignments.deleteAll(assignments.findByClientIdOrderByValidFromDesc(sourceId));
        assignments.flush();
        clients.delete(source);
        clients.flush();
        audit.record("CLIENT", sourceId, "MERGE", before, new MergedInto(targetId, target.getName(), maskedPan(target)));
        audit.record("CLIENT", targetId, "MERGE_IN", null, new MergedFrom(sourceId, source.getName(), maskedPan(target), why));
    }

    private String maskedPan(Client c) {
        return c.getPanEncrypted() == null ? null : PanCipher.mask(panCipher.decrypt(c.getPanEncrypted()));
    }

    record MergeAudit(String source, String sourcePan, String target, String targetPan, List<String> codes, List<AuditView> rmHistory,
                      String reason) {
    }

    record MergedInto(long clientId, String name, String panNow) {
    }

    record MergedFrom(long clientId, String name, String panNow, String reason) {
    }

    private AuditView auditView(ClientRmAssignment a) {
        return new AuditView(a.getClientId(), a.getRmId(), a.getValidFrom(), a.getValidTo(), a.getReason());
    }

    private static <T> List<List<T>> chunks(List<T> all, int size) {
        return java.util.stream.IntStream.range(0, (all.size() + size - 1) / size)
                .mapToObj(i -> all.subList(i * size, Math.min(all.size(), (i + 1) * size))).toList();
    }

    private record Row(long id, String name, String panEncrypted, String code, Long rmId, long total) {
    }

    /** {@code pan} is masked unless the caller is FINANCE or ADMIN. */
    public record ClientView(Long id, String name, String pan, String iwellCode, Long rmId, String rmName, String branchName) {
    }

    public record ClientDetail(ClientView client, String iwellCode2, String familyHeadCode, String familyHeadName) {
    }

    /** {@code nameKey} is {@link Text#key} of the name; {@code panHash} as made by PanCipher.hash (null = no PAN). */
    public record ClientKey(long id, String nameKey, String panHash) {
    }

    public record AuditView(Long clientId, Long rmId, LocalDate validFrom, LocalDate validTo, String reason) {
    }
}
