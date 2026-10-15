package money.epsilon.brokerage.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One audit record. Insert-only; the table also rejects UPDATE/DELETE at database level. */
@Entity
@Immutable
@Table(name = "audit_log")
class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    @Column(name = "by_user")
    private String byUser;

    private Instant at;

    protected AuditEntry() {
    }

    AuditEntry(String entity, String entityId, String action, String beforeJson, String afterJson, String byUser, Instant at) {
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.byUser = byUser;
        this.at = at;
    }

    Long getId() { return id; }
    String getEntity() { return entity; }
    String getEntityId() { return entityId; }
    String getAction() { return action; }
    String getBeforeJson() { return beforeJson; }
    String getAfterJson() { return afterJson; }
    String getByUser() { return byUser; }
    Instant getAt() { return at; }
}
