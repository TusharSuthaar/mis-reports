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

/** One client's PMS investment, as entered on the PMS screen (a row of the PMS register, R-85). */
@Entity
@Table(name = "pms_investment")
public class PmsInvestment {

    public enum PayoutFrequency { MONTHLY, QUARTERLY, NONE }

    public enum Certificate { PENDING, RECEIVED, NOT_APPLICABLE }

    public enum Holding { ACTIVE, REDEEMED }

    public enum Status { ACTIVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate month;
    @Column(name = "rm_id") private Long rmId;
    @Column(name = "partner_id") private Long partnerId;
    @Column(name = "client_id") private Long clientId;
    private String mobile;
    private String email;
    private String address;
    private String amc;
    private String strategy;
    private BigDecimal amount;
    @Column(name = "investment_date") private LocalDate investmentDate;
    @Column(name = "on_portal") private boolean onPortal;
    @Column(name = "trail_percent") private BigDecimal trailPercent;
    @Enumerated(EnumType.STRING) @Column(name = "payout_frequency") private PayoutFrequency payoutFrequency;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal consideration;
    @Enumerated(EnumType.STRING) private Certificate certificate;
    @Column(name = "investment_mail") private boolean investmentMail;
    @Enumerated(EnumType.STRING) @Column(name = "holding_status") private Holding holdingStatus;
    @Column(name = "redeemed_on") private LocalDate redeemedOn;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected PmsInvestment() {
    }

    public PmsInvestment(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Sets every entered field. A stock transfer's consideration is qty × price when both are given. */
    public void fill(Details d) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.amc = d.amc();
        this.strategy = d.strategy();
        this.amount = d.amount();
        this.investmentDate = d.investmentDate();
        this.onPortal = d.onPortal();
        this.trailPercent = d.trailPercent();
        this.payoutFrequency = d.payoutFrequency() == null ? PayoutFrequency.QUARTERLY : d.payoutFrequency();
        this.qty = d.qty();
        this.price = d.price();
        this.consideration = d.qty() != null && d.price() != null
                ? d.qty().multiply(d.price()).setScale(2, java.math.RoundingMode.HALF_UP) : d.consideration();
        this.certificate = d.certificate() == null ? Certificate.PENDING : d.certificate();
        this.investmentMail = d.investmentMail();
        this.holdingStatus = d.holdingStatus() == null ? Holding.ACTIVE : d.holdingStatus();
        this.redeemedOn = this.holdingStatus == Holding.REDEEMED ? d.redeemedOn() : null;
        this.remarks = d.remarks();
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

    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String amc, String strategy,
                          BigDecimal amount, LocalDate investmentDate, boolean onPortal, BigDecimal trailPercent, PayoutFrequency payoutFrequency,
                          BigDecimal qty, BigDecimal price, BigDecimal consideration, Certificate certificate, boolean investmentMail,
                          Holding holdingStatus, LocalDate redeemedOn, String remarks) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getAmc() { return amc; }
    public String getStrategy() { return strategy; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getInvestmentDate() { return investmentDate; }
    public boolean isOnPortal() { return onPortal; }
    public BigDecimal getTrailPercent() { return trailPercent; }
    public PayoutFrequency getPayoutFrequency() { return payoutFrequency; }
    public BigDecimal getQty() { return qty; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getConsideration() { return consideration; }
    public Certificate getCertificate() { return certificate; }
    public boolean isInvestmentMail() { return investmentMail; }
    public Holding getHoldingStatus() { return holdingStatus; }
    public LocalDate getRedeemedOn() { return redeemedOn; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
