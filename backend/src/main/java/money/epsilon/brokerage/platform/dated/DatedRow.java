package money.epsilon.brokerage.platform.dated;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/**
 * Base for effective-dated rows (assignments, links). Both ends are inclusive; validTo null = still current.
 * Rows are never edited except to close them (set validTo) — see {@link Timeline}.
 */
@MappedSuperclass
public abstract class DatedRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    private String reason;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    protected DatedRow() {
    }

    protected DatedRow(LocalDate validFrom, LocalDate validTo, String reason) {
        if (validFrom == null) throw new IllegalArgumentException("A start date is required");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("End date is before start date");
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.reason = reason;
        stamp();
    }

    /** Ends this row on {@code lastDay} (inclusive). The reason for ending goes to the audit log, not here. */
    public void closeOn(LocalDate lastDay) {
        if (lastDay.isBefore(validFrom)) {
            throw new IllegalArgumentException("Cannot end on " + lastDay + ": it starts on " + validFrom);
        }
        this.validTo = lastDay;
        stamp();
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(validFrom) && (validTo == null || !day.isAfter(validTo));
    }

    private void stamp() {
        this.changedBy = CurrentUser.username();
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public String getReason() { return reason; }

    /** For a correction of a row (audited by the caller). */
    protected void setReason(String reason) {
        this.reason = reason;
    }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
