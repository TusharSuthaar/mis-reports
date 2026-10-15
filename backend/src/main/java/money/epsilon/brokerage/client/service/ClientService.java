package money.epsilon.brokerage.client.service;

import java.time.LocalDate;
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

    ClientService(ClientRepository clients, ClientExternalIdRepository externalIds, ClientRmAssignmentRepository assignments,
                  EmployeeService employees, PanCipher panCipher, AuditService audit, JdbcClient jdbc, ChangeWindow window) {
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

    /** Page of clients sorted by name. {@code q} matches name or any source code (Finesse shown first); {@code needsRm} = no current RM. */
    @Transactional(readOnly = true)
    public PageResponse<ClientView> page(String q, boolean needsRm, Long rmId, int page, int size) {
        String query = Text.clean(q) == null ? "" : Text.clean(q).toLowerCase();
        List<Row> rows = jdbc.sql("""
                        SELECT c.id, c.name, c.pan_encrypted, x.code, a.rm_id, count(*) OVER () AS total
                        FROM client c
                        LEFT JOIN LATERAL (SELECT code FROM client_external_id e WHERE e.client_id = c.id
                                           ORDER BY e.source = :source DESC, e.code LIMIT 1) x ON true
                        LEFT JOIN client_rm_assignment a ON a.client_id = c.id AND a.valid_to IS NULL
                        WHERE (:q = '' OR lower(c.name) LIKE :like
                               OR EXISTS (SELECT 1 FROM client_external_id e WHERE e.client_id = c.id AND lower(e.code) LIKE :like))
                          AND (:needsRm = false OR a.id IS NULL)
                          AND (:rmId = 0 OR a.rm_id = :rmId)
                        ORDER BY lower(c.name), c.id
                        LIMIT :size OFFSET :offset""")
                .param("source", "FINESSE").param("q", query).param("like", "%" + query + "%")
                .param("needsRm", needsRm).param("rmId", rmId == null ? 0L : rmId)
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

    /** The client's RM is {@code rmId} from {@code from}; the previous RM ends the day before. History is kept. */
    @Transactional
    public void changeRm(long clientId, long rmId, LocalDate from, String reason) {
        Client c = find(clientId);
        window.beforeChange(from);
        EmployeeView rm = employees.requireEmployedOn(rmId, from);
        Optional<ClientRmAssignment> latest = assignments.findFirstByClientIdOrderByValidFromDesc(clientId);
        if (latest.filter(a -> a.getValidTo() == null && a.getRmId().equals(rmId)).isPresent()) {
            throw new RuleViolationException(c.getName() + " is already with " + rm.name());
        }
        DatedHistory.makeRoom(latest, from, assignments, audit, AUDIT_CLIENT_RM, this::auditView, reason);
        DatedHistory.add(new ClientRmAssignment(clientId, rmId, from, reason), assignments, audit, AUDIT_CLIENT_RM, this::auditView);
    }

    /**
     * The client's RM is {@code rmId} from {@code from}, as a master file says. Same RM: nothing. An entry that starts on
     * {@code from} is corrected in place (it was wrong from its first day); otherwise this is an ordinary change.
     */
    @Transactional
    public void setRmFrom(long clientId, long rmId, LocalDate from, String reason) {
        Optional<ClientRmAssignment> latest = assignments.findFirstByClientIdOrderByValidFromDesc(clientId);
        if (latest.isEmpty() || latest.get().getValidTo() != null || !latest.get().getValidFrom().equals(from)) {
            if (latest.filter(a -> a.getValidTo() == null && a.getRmId().equals(rmId)).isPresent()) return;
            changeRm(clientId, rmId, from, reason);
            return;
        }
        ClientRmAssignment a = latest.get();
        if (a.getRmId().equals(rmId)) return;
        window.beforeChange(from);
        employees.requireEmployedOn(rmId, from);
        AuditView before = auditView(a);
        a.correctRm(rmId, reason);
        assignments.saveAndFlush(a);
        audit.record(AUDIT_CLIENT_RM, clientId, "CORRECT", before, auditView(a));
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

    /** client id → name. */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> clientIds) {
        Map<Long, String> result = new HashMap<>();
        if (clientIds.isEmpty()) return result;
        jdbc.sql("SELECT id, name FROM client WHERE id IN (:ids)").param("ids", clientIds)
                .query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list().forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
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
