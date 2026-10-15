package money.epsilon.brokerage.ingestion.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An uploaded income file for one month. STAGED → COMMITTED (→ SUPERSEDED by a later upload), or DISCARDED. */
@Entity
@Table(name = "upload")
class Upload {

    enum Status { STAGED, COMMITTED, SUPERSEDED, DISCARDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;

    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    private String sha256;

    @Enumerated(EnumType.STRING)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "totals_json")
    private String totalsJson;

    private int warnings;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "committed_by")
    private String committedBy;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "posted_to_period_id")
    private Long postedToPeriodId;

    protected Upload() {
    }

    Upload(String source, Long periodId, String fileName, String filePath, String sha256, String totalsJson, int warnings, String uploadedBy) {
        this.source = source;
        this.periodId = periodId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.sha256 = sha256;
        this.totalsJson = totalsJson;
        this.warnings = warnings;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.status = Status.STAGED;
    }

    void commit(String by) {
        requireStaged();
        status = Status.COMMITTED;
        committedBy = by;
        committedAt = Instant.now();
    }

    /** A correction to a locked month: its differences were posted into {@code periodId}. */
    void postedTo(long periodId) {
        this.postedToPeriodId = periodId;
    }

    void discard() {
        requireStaged();
        status = Status.DISCARDED;
    }

    void supersede() {
        if (status != Status.COMMITTED) throw new IllegalStateException("Only a saved upload can be replaced");
        status = Status.SUPERSEDED;
    }

    private void requireStaged() {
        if (status != Status.STAGED) throw new RuleViolationException("This upload is already " + status.name().toLowerCase());
    }

    Long getId() { return id; }
    String getSource() { return source; }
    Long getPeriodId() { return periodId; }
    String getFileName() { return fileName; }
    String getSha256() { return sha256; }
    Status getStatus() { return status; }
    String getTotalsJson() { return totalsJson; }
    int getWarnings() { return warnings; }
    String getUploadedBy() { return uploadedBy; }
    Instant getUploadedAt() { return uploadedAt; }
    String getCommittedBy() { return committedBy; }
    Instant getCommittedAt() { return committedAt; }
    Long getPostedToPeriodId() { return postedToPeriodId; }
}
