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
 * keeps resolving). Only code, name, family head and the two PANs are kept.
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

    private final JdbcClient jdbc;
    private final PanCipher panCipher;
    private final AuditService audit;

    FinesseClientMasterService(JdbcClient jdbc, PanCipher panCipher, AuditService audit) {
        this.jdbc = jdbc;
        this.panCipher = panCipher;
        this.audit = audit;
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
        try (ExcelFile file = ExcelFile.open(content)) {
            read = file.table(SHEET, REQUIRED, 15).or(() -> file.table(null, REQUIRED, 15)).orElseThrow();
            if (!read.missing().isEmpty()) {
                throw new RuleViolationException("This is not the Finesse client master: columns missing " + String.join(", ", read.missing())
                        + " (sheets: " + String.join(", ", file.sheetNames()) + ")");
            }
        }
        Map<String, Code> existing = all();
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
            Code was = existing.get(code);
            if (was != null && was.name().equals(name) && eq(was.pan(), pan) && eq(was.guardianPan(), guardian) && eq(was.familyHeadCode(), head)) {
                unchanged++;
                continue;
            }
            jdbc.sql("""
                            INSERT INTO finesse_client (code, name, family_head_code, pan_encrypted, pan_hash, guardian_pan_encrypted,
                                                        guardian_pan_hash, updated_by)
                            VALUES (:code, :name, :head, :pe, :ph, :ge, :gh, :by)
                            ON CONFLICT (code) DO UPDATE SET name = excluded.name, family_head_code = excluded.family_head_code,
                                pan_encrypted = excluded.pan_encrypted, pan_hash = excluded.pan_hash,
                                guardian_pan_encrypted = excluded.guardian_pan_encrypted, guardian_pan_hash = excluded.guardian_pan_hash,
                                updated_at = now(), updated_by = excluded.updated_by""")
                    .param("code", code).param("name", name).param("head", head)
                    .param("pe", pan == null ? null : panCipher.encrypt(pan)).param("ph", pan == null ? null : panCipher.hash(pan))
                    .param("ge", guardian == null ? null : panCipher.encrypt(guardian)).param("gh", guardian == null ? null : panCipher.hash(guardian))
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
