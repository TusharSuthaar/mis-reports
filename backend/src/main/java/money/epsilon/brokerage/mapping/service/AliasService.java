package money.epsilon.brokerage.mapping.service;

import java.util.Optional;
import money.epsilon.brokerage.mapping.domain.SourceAlias;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.repository.SourceAliasRepository;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers which RM / partner a spelling in a source file stands for, so the same spelling resolves by itself
 * next month. Spellings are compared after {@link #normalise}.
 */
@Service
public class AliasService {

    private final SourceAliasRepository aliases;
    private final AuditService audit;

    AliasService(SourceAliasRepository aliases, AuditService audit) {
        this.aliases = aliases;
        this.audit = audit;
    }

    /** Upper-case, trimmed, inner whitespace collapsed. Blank → empty string. */
    public static String normalise(String raw) {
        return Text.key(raw);
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolve(String source, EntityType type, String raw) {
        return aliases.findBySourceAndEntityTypeAndRawValueNormalized(source, type, normalise(raw)).map(SourceAlias::getTargetId);
    }

    /**
     * Records the alias; saving the same mapping again is a no-op, a different target for the same spelling is refused.
     * @return true if a new alias was saved
     */
    @Transactional
    public boolean remember(String source, EntityType type, String raw, long targetId) {
        String key = normalise(raw);
        if (key.isEmpty()) throw new IllegalArgumentException("An alias needs a spelling");
        Optional<SourceAlias> existing = aliases.findBySourceAndEntityTypeAndRawValueNormalized(source, type, key);
        if (existing.isPresent()) {
            if (existing.get().getTargetId() == targetId) return false;
            throw new RuleViolationException("'" + raw + "' already points to another " + type.name().toLowerCase());
        }
        SourceAlias saved = aliases.save(new SourceAlias(source, type, key, targetId, CurrentUser.username()));
        audit.record("SOURCE_ALIAS", saved.getId(), "CREATE", null, new AliasView(source, type, key, targetId));
        return true;
    }

    /** Points every alias of one RM / partner at another (after a merge). @return how many moved */
    @Transactional
    public int retarget(EntityType type, long fromId, long toId) {
        int moved = aliases.retarget(type, fromId, toId);
        if (moved > 0) audit.record("SOURCE_ALIAS", fromId, "RETARGET", null, new Retarget(type, fromId, toId, moved));
        return moved;
    }

    record Retarget(EntityType type, long fromId, long toId, int aliases) {
    }

    public record AliasView(String source, EntityType type, String spelling, long targetId) {
    }
}
