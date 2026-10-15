package money.epsilon.brokerage.platform.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A unit of background work. Moves QUEUED → RUNNING → DONE | FAILED, never backwards. */
@Entity
@Table(name = "job")
class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    private String error;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** The reference of the request that submitted it: the same in the logs and on the Activity screen. */
    @Column(name = "request_id")
    private String requestId;

    protected Job() {
    }

    Job(String type, String payload, String createdBy) {
        this(type, payload, createdBy, null);
    }

    Job(String type, String payload, String createdBy, String requestId) {
        this.requestId = requestId;
        this.type = type;
        this.payload = payload;
        this.createdBy = createdBy;
        this.status = JobStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    void start() {
        requireStatus(JobStatus.QUEUED);
        status = JobStatus.RUNNING;
        startedAt = Instant.now();
    }

    void complete(String result) {
        requireStatus(JobStatus.RUNNING);
        this.status = JobStatus.DONE;
        this.result = result;
        this.finishedAt = Instant.now();
    }

    /** Allowed from QUEUED too, e.g. when a restart interrupts work that never started. */
    void fail(String error) {
        if (status == JobStatus.DONE || status == JobStatus.FAILED) {
            throw new IllegalStateException("Job " + id + " is already " + status);
        }
        this.status = JobStatus.FAILED;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Job " + id + " is " + status + ", expected " + expected);
        }
    }

    Long getId() { return id; }
    String getType() { return type; }
    JobStatus getStatus() { return status; }
    String getPayload() { return payload; }
    String getResult() { return result; }
    String getError() { return error; }
    String getCreatedBy() { return createdBy; }
    Instant getCreatedAt() { return createdAt; }
    Instant getStartedAt() { return startedAt; }
    Instant getFinishedAt() { return finishedAt; }
    String getRequestId() { return requestId; }
}
