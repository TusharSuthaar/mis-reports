package money.epsilon.brokerage.pms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/**
 * One client's PMS revenue for a month (R-85): typed for the month the AMC's payout is received, against the client's
 * PMS investment. The amount is what the END's PMS column shows (excl. GST).
 */
@Entity
@Table(name = "pms_fee")
public class PmsFee {

    public enum Status { ACTIVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investment_id") private Long investmentId;
    private LocalDate month;
    private BigDecimal amount;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected PmsFee() {
    }

    public PmsFee(long investmentId) {
        this.investmentId = investmentId;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    public void set(LocalDate month, BigDecimal amount, String remarks) {
        this.month = month;
        this.amount = amount;
        this.remarks = remarks;
        touch();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        touch();
    }

    private void touch() {
        this.updatedBy = CurrentUser.username();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInvestmentId() { return investmentId; }
    public LocalDate getMonth() { return month; }
    public BigDecimal getAmount() { return amount; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
