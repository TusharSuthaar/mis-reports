package money.epsilon.brokerage.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records who changed what. Call it inside the transaction that makes the change, so the change and its
 * audit record commit (or roll back) together.
 */
@Service
public class AuditService {

    private final AuditRepository repository;
    private final ObjectMapper mapper;

    AuditService(AuditRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** {@code before}/{@code after} are serialised to JSON; pass views, never entities holding secrets. */
    @Transactional
    public void record(String entity, Object entityId, String action, Object before, Object after) {
        repository.save(new AuditEntry(entity, entityId == null ? null : entityId.toString(), action,
                toJson(before), toJson(after), CurrentUser.username(), Instant.now()));
    }

    /**
     * The newest audit entry's id (0 when none). Every saved change writes an audit entry, so this number changes whenever
     * anything changes: read-mostly lookups (the org chart) use it to know a remembered answer is still current.
     */
    @Transactional(readOnly = true)
    public long lastId() {
        return repository.findTopByOrderByIdDesc().map(AuditEntry::getId).orElse(0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> search(AuditFilter filter, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("at"), Sort.Order.desc("id")));
        return PageResponse.of(repository.findAll(toSpec(filter), pageable), this::toView);
    }

    private static Specification<AuditEntry> toSpec(AuditFilter f) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            if (f.entity() != null) p.add(cb.equal(root.get("entity"), f.entity()));
            if (f.entityId() != null) p.add(cb.equal(root.get("entityId"), f.entityId()));
            if (f.action() != null) p.add(cb.equal(root.get("action"), f.action()));
            if (f.by() != null) p.add(cb.equal(cb.lower(root.get("byUser")), f.by().toLowerCase()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("at"), f.from()));
            if (f.to() != null) p.add(cb.lessThan(root.get("at"), f.to()));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private AuditView toView(AuditEntry e) {
        return new AuditView(e.getId(), e.getEntity(), e.getEntityId(), e.getAction(),
                fromJson(e.getBeforeJson()), fromJson(e.getAfterJson()), e.getByUser(), e.getAt());
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise audit value", e);
        }
    }

    private JsonNode fromJson(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt audit JSON", e);
        }
    }

    /** All fields optional; {@code from} inclusive, {@code to} exclusive. */
    public record AuditFilter(String entity, String entityId, String action, String by, Instant from, Instant to) {
    }

    public record AuditView(Long id, String entity, String entityId, String action,
                            JsonNode before, JsonNode after, String by, Instant at) {
    }
}
