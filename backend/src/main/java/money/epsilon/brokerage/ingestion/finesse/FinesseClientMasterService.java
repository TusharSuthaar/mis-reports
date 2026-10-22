package money.epsilon.brokerage.ingestion.finesse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.excel.ExcelFile;
import money.epsilon.brokerage.platform.excel.ExcelRow;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Finesse client master export (ClientMasterReport): which PAN, or for a minor which guardian's PAN, each Finesse
 * client code belongs to. Each upload adds new codes and updates known ones; a code is never removed (old brokerage
 * keeps resolving). Code, name, family head and the two PANs are kept for matching; every other filled column of the
 * client sheet, and the client's rows of the "Client Address Master" sheet, are kept encrypted for the client's page (R-08).
 */
@Service
public class FinesseClientMasterService {

    static final String SHEET = "ClientMasterReport";
    static final String CODE = "CLIENT CODE";
    static final String NAME = "CLIENT NAME";
    static final String PAN = "PAN NUMBER";
    static final String GUARDIAN_PAN = "GUARDIAN PAN";
    static final String FAMILY_HEAD_CODE = "FAMILY HEAD CODE";
    static final List<String> REQUIRED = List.of(CODE, NAME, PAN, GUARDIAN_PAN);
    static final String ADDRESS_SHEET = "Client Address Master";
    /** Kept in their own columns (and shown from there), so not repeated in the details. */
    private static final java.util.Set<String> OWN_COLUMNS = java.util.Set.of(Text.key(CODE), Text.key(NAME), Text.key(PAN), Text.key(GUARDIAN_PAN));

    private final JdbcClient jdbc;
    private final PanCipher panCipher;
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    FinesseClientMasterService(JdbcClient jdbc, PanCipher panCipher, AuditService audit, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.panCipher = panCipher;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** One filled column of the Ops master, header as written. */
    public record Field(String label, String value) {
    }

    /** All the Ops master has for one client code (R-08). PANs masked unless the user may see them in full. */
    public record OpsRecord(String code, String name, String pan, String guardianPan, String familyHeadCode, List<Field> details,
                            List<List<Field>> addresses, Instant updatedAt) {
    }

    public record LoadResult(String fileName, int rows, int added, int updated, int unchanged, int withPan, int withGuardianPan,
                             int withoutAnyPan, int invalidPans) {
    }

    public record Status(long codes, long withPan, long withGuardianPan, Instant updatedAt, String updatedBy) {
    }

    /** What one code resolves to. PANs are plain (normalised) for the caller to match / create clients with. */
    public record Code(String code, String name, String familyHeadCode, String pan, String panHash, String guardianPan,
                       String guardianPanHash) {
    }

    @Transactional
    public LoadResult load(byte[] content, String fileName) {
        ExcelFile.Table read;
        Map<String, List<List<Field>>> addressesByCode = new HashMap<>();
        try (ExcelFile file = ExcelFile.open(content)) {
            read = file.table(SHEET, REQUIRED, 15).or(() -> file.table(null, REQUIRED, 15)).orElseThrow();
            if (!read.missing().isEmpty()) {
                throw new RuleViolationException("This is not the Finesse client master: columns missing " + String.join(", ", read.missing())
                        + " (sheets: " + String.join(", ", file.sheetNames()) + ")");
            }
            // The address sheet is optional: an export without it keeps the addresses already loaded.
            file.table(ADDRESS_SHEET, List.of(CODE), 15).filter(t -> t.missing().isEmpty()).ifPresent(t -> {
                for (ExcelRow a : t.rows()) {
                    String code = a.text(CODE).toUpperCase();
                    if (code.isEmpty()) continue;
                    addressesByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(fields(a, true));
                }
            });
        }
        boolean withAddresses = !addressesByCode.isEmpty();
        Map<String, Code> existing = all();
        Map<String, String[]> existingDetails = new HashMap<>();
        jdbc.sql("SELECT code, details_encrypted, addresses_encrypted FROM finesse_client")
                .query((rs, n) -> Map.entry(rs.getString(1), new String[]{decryptOrNull(rs.getString(2)), decryptOrNull(rs.getString(3))}))
                .list().forEach(e -> existingDetails.put(e.getKey(), e.getValue()));
        Set<String> seen = new HashSet<>();
        int added = 0, updated = 0, unchanged = 0, withPan = 0, withGuardian = 0, none = 0, invalid = 0;
        String by = CurrentUser.username();
        for (ExcelRow l : read.rows()) {
            String code = l.text(CODE).toUpperCase();
            if (code.isEmpty()) continue;
            if (!seen.add(code)) throw new RuleViolationException("Row " + l.rowNumber() + ": client code " + code + " appears more than once");
            String name = Text.clean(l.text(NAME));
            if (name == null) name = code;
            String pan = PanCipher.normaliseOrNull(l.text(PAN));
            String guardian = PanCipher.normaliseOrNull(l.text(GUARDIAN_PAN));
            if (pan == null && !l.text(PAN).isBlank()) invalid++;
            if (guardian == null && !l.text(GUARDIAN_PAN).isBlank()) invalid++;
            if (pan != null) withPan++;
            if (guardian != null) withGuardian++;
            if (pan == null && guardian == null) none++;
            String head = Text.clean(l.text(FAMILY_HEAD_CODE));
            String details = json(fields(l, false));
            String[] before = existingDetails.getOrDefault(code, new String[2]);
            String addresses = withAddresses ? (addressesByCode.containsKey(code) ? json(addressesByCode.get(code)) : null) : before[1];
            Code was = existing.get(code);
            if (was != null && was.name().equals(name) && eq(was.pan(), pan) && eq(was.guardianPan(), guardian) && eq(was.familyHeadCode(), head)
                    && eq(before[0], details) && eq(before[1], addresses)) {
                unchanged++;
                continue;
            }
            jdbc.sql("""
                            INSERT INTO finesse_client (code, name, family_head_code, pan_encrypted, pan_hash, guardian_pan_encrypted,
                                                        guardian_pan_hash, details_encrypted, addresses_encrypted, updated_by)
                            VALUES (:code, :name, :head, :pe, :ph, :ge, :gh, :de, :ae, :by)
                            ON CONFLICT (code) DO UPDATE SET name = excluded.name, family_head_code = excluded.family_head_code,
                                pan_encrypted = excluded.pan_encrypted, pan_hash = excluded.pan_hash,
                                guardian_pan_encrypted = excluded.guardian_pan_encrypted, guardian_pan_hash = excluded.guardian_pan_hash,
                                details_encrypted = excluded.details_encrypted, addresses_encrypted = excluded.addresses_encrypted,
                                updated_at = now(), updated_by = excluded.updated_by""")
                    .param("code", code).param("name", name).param("head", head)
                    .param("pe", pan == null ? null : panCipher.encrypt(pan)).param("ph", pan == null ? null : panCipher.hash(pan))
                    .param("ge", guardian == null ? null : panCipher.encrypt(guardian)).param("gh", guardian == null ? null : panCipher.hash(guardian))
                    .param("de", details == null ? null : panCipher.encrypt(details))
                    .param("ae", addresses == null ? null : panCipher.encrypt(addresses))
                    .param("by", by)
                    .update();
            if (was == null) added++;
            else updated++;
        }
        if (seen.isEmpty()) throw new RuleViolationException("The client master has no client rows");
        LoadResult result = new LoadResult(fileName, seen.size(), added, updated, unchanged, withPan, withGuardian, none, invalid);
        audit.record("FINESSE_CLIENT_MASTER", null, "LOAD", null, result);
        return result;
    }

    @Transactional(readOnly = true)
    public Status status() {
        return jdbc.sql("""
                        SELECT count(*) AS codes, count(pan_hash) AS pans, count(guardian_pan_hash) AS guardians,
                               max(updated_at) AS at,
                               (SELECT updated_by FROM finesse_client ORDER BY updated_at DESC LIMIT 1) AS by
                        FROM finesse_client""")
                .query((rs, n) -> new Status(rs.getLong("codes"), rs.getLong("pans"), rs.getLong("guardians"),
                        rs.getTimestamp("at") == null ? null : rs.getTimestamp("at").toInstant(), rs.getString("by")))
                .single();
    }

    /**
     * The Ops master's records for one client (R-08): the codes whose PAN is the client's PAN or one of its former PANs,
     * and the codes linked to it by brokerage uploads (a minor's code carries the guardian's PAN). Newest first.
     */
    @Transactional(readOnly = true)
    public List<OpsRecord> records(String panHash, Collection<String> formerPanHashes, Collection<String> codes, boolean fullPan) {
        List<String> hashes = new ArrayList<>(formerPanHashes);
        if (panHash != null) hashes.add(panHash);
        if (hashes.isEmpty()) hashes.add("-");
        List<String> codeList = codes.isEmpty() ? List.of("-") : List.copyOf(codes);
        return jdbc.sql("""
                        SELECT * FROM finesse_client WHERE pan_hash IN (:hashes) OR code IN (:codes)
                        ORDER BY updated_at DESC, code""")
                .param("hashes", hashes).param("codes", codeList)
                .query((rs, n) -> {
                    Code c = code(rs);
                    List<Field> details = parse(decryptOrNull(rs.getString("details_encrypted")), new com.fasterxml.jackson.core.type.TypeReference<List<Field>>() { });
                    List<List<Field>> addresses = parse(decryptOrNull(rs.getString("addresses_encrypted")),
                            new com.fasterxml.jackson.core.type.TypeReference<List<List<Field>>>() { });
                    return new OpsRecord(c.code(), c.name(), show(c.pan(), fullPan), show(c.guardianPan(), fullPan), c.familyHeadCode(),
                            details == null ? List.of() : details.stream().map(f -> masked(f, fullPan)).toList(),
                            addresses == null ? List.of() : addresses.stream().map(a -> a.stream().map(f -> masked(f, fullPan)).toList()).toList(),
                            rs.getTimestamp("updated_at").toInstant());
                })
                .list();
    }

    /** The codes of minors under this guardian's PAN: codes without a PAN of their own whose guardian PAN is this one (R-03). */
    @Transactional(readOnly = true)
    public List<String> minorCodes(String guardianPanHash) {
        return jdbc.sql("SELECT code FROM finesse_client WHERE guardian_pan_hash = ? AND pan_hash IS NULL ORDER BY code")
                .param(guardianPanHash).query(String.class).list();
    }

    /** A joint holder's PAN (any column named "…PAN…") is masked like the client's own. */
    private static Field masked(Field f, boolean fullPan) {
        return !fullPan && f.label().toUpperCase().contains("PAN") ? new Field(f.label(), PanCipher.mask(f.value())) : f;
    }

    private static String show(String pan, boolean fullPan) {
        return pan == null ? null : fullPan ? pan : PanCipher.mask(pan);
    }

    /** The row's filled columns; {@code address}: the address sheet's row, without the code, name, PAN, family and RM. */
    private static List<Field> fields(ExcelRow row, boolean address) {
        java.util.Set<String> skip = address
                ? java.util.Set.of(Text.key(CODE), Text.key(NAME), Text.key(PAN), Text.key("Family"), Text.key("RM"), Text.key("SRM"))
                : OWN_COLUMNS;
        return row.cells().stream().filter(c -> !skip.contains(Text.key(c.header()))).map(c -> new Field(c.header(), c.text())).toList();
    }

    private String json(Object value) {
        try {
            return value instanceof List<?> l && l.isEmpty() ? null : mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T parse(String json, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String decryptOrNull(String stored) {
        return stored == null ? null : panCipher.decrypt(stored);
    }

    /** code → what the client master says, for the codes it knows. */
    @Transactional(readOnly = true)
    public Map<String, Code> find(Collection<String> codes) {
        Map<String, Code> result = new HashMap<>();
        List<String> list = new ArrayList<>(codes);
        for (int i = 0; i < list.size(); i += 1000) {
            List<String> chunk = list.subList(i, Math.min(list.size(), i + 1000));
            jdbc.sql("SELECT * FROM finesse_client WHERE code IN (:codes)").param("codes", chunk)
                    .query((rs, n) -> code(rs)).list().forEach(c -> result.put(c.code(), c));
        }
        return result;
    }

    private Map<String, Code> all() {
        Map<String, Code> result = new HashMap<>();
        jdbc.sql("SELECT * FROM finesse_client").query((rs, n) -> code(rs)).list().forEach(c -> result.put(c.code(), c));
        return result;
    }

    private Code code(java.sql.ResultSet rs) throws java.sql.SQLException {
        String pe = rs.getString("pan_encrypted");
        String ge = rs.getString("guardian_pan_encrypted");
        return new Code(rs.getString("code"), rs.getString("name"), rs.getString("family_head_code"),
                pe == null ? null : panCipher.decrypt(pe), rs.getString("pan_hash"),
                ge == null ? null : panCipher.decrypt(ge), rs.getString("guardian_pan_hash"));
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
