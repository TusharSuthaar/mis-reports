package money.epsilon.brokerage.period.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/** One calendar month for one product. OPEN → PROCESSED → LOCKED. */
@Entity
@Table(name = "period")
public class Period {

    public enum Status { OPEN, PROCESSED, LOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_type")
    private String productType;

    private LocalDate month;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Period() {
    }

    public Period(String productType, YearMonth month) {
        this.productType = productType;
        this.month = month.atDay(1);
        this.fromDate = month.atDay(1);
        this.toDate = month.atEndOfMonth();
        this.status = Status.OPEN;
        this.createdAt = Instant.now();
    }

    /** The split was (re)run. */
    public void markProcessed(String by) {
        if (status == Status.LOCKED) throw new IllegalStateException("A locked month cannot be processed");
        status = Status.PROCESSED;
        processedBy = by;
        processedAt = Instant.now();
    }

    /** Its figures changed: it must be processed again. */
    public void reopen() {
        if (status == Status.LOCKED) throw new IllegalStateException("A locked month cannot be reopened this way");
        status = Status.OPEN;
    }

    public void lock(String by) {
        if (status != Status.PROCESSED) throw new IllegalStateException("Only a processed month can be locked");
        status = Status.LOCKED;
        lockedBy = by;
        lockedAt = Instant.now();
    }

    public void unlock() {
        if (status != Status.LOCKED) throw new IllegalStateException("The month is not locked");
        status = Status.PROCESSED;
        lockedBy = null;
        lockedAt = null;
    }

    public Long getId() { return id; }
    public String getProductType() { return productType; }
    public LocalDate getMonth() { return month; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public Status getStatus() { return status; }
    public String getProcessedBy() { return processedBy; }
    public Instant getProcessedAt() { return processedAt; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
}
