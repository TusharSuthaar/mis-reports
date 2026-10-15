package money.epsilon.brokerage.mapping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A spelling seen in a source file, and the RM or partner it stands for. */
@Entity
@Table(name = "source_alias")
public class SourceAlias {

    public enum EntityType { RM, PARTNER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type")
    private EntityType entityType;

    @Column(name = "raw_value_normalized")
    private String rawValueNormalized;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SourceAlias() {
    }

    public SourceAlias(String source, EntityType entityType, String rawValueNormalized, Long targetId, String createdBy) {
        this.source = source;
        this.entityType = entityType;
        this.rawValueNormalized = rawValueNormalized;
        this.targetId = targetId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public EntityType getEntityType() { return entityType; }
    public String getRawValueNormalized() { return rawValueNormalized; }
    public Long getTargetId() { return targetId; }
}
