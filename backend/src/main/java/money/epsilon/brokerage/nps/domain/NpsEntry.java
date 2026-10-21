package money.epsilon.brokerage.nps.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/** One NPS investment, as entered on the NPS screen (a row of the NPS register, R-86). */
@Entity
@Table(name = "nps_entry")
public class NpsEntry {

    public enum Certificate { PENDING, RECEIVED, NOT_APPLICABLE }

    public enum Status { ACTIVE, CANCELLED }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
    private String pop;
    @Column(name = "investment_date") private LocalDate investmentDate;
    private BigDecimal amount;
    @Column(name = "welcome_mail") private boolean welcomeMail;
    @Column(name = "investment_mail") private boolean investmentMail;
    @Enumerated(EnumType.STRING) private Certificate certificate;
    @Column(name = "on_portal") private boolean onPortal;
    @Column(name = "revenue_remarks") private String revenueRemarks;
    @Column(name = "pop_charges") private BigDecimal popCharges;
    @Column(name = "share_percent") private BigDecimal sharePercent;
    private BigDecimal revenue;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal invoice;
    @Column(name = "received_on") private LocalDate receivedOn;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected NpsEntry() {
    }

    public NpsEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /**
     * Sets every entered field (R-86). Revenue (invoice amount without GST) = POP charges × sharing % when both are given,
     * else as typed (none = 0). GST = revenue × the NPS GST % (shown as CGST + SGST, half each); invoice = revenue + GST.
     */
    public void fill(Details d, BigDecimal gstPercent) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.pop = d.pop();
        this.investmentDate = d.investmentDate();
        this.amount = d.amount();
        this.welcomeMail = d.welcomeMail();
        this.investmentMail = d.investmentMail();
        this.certificate = d.certificate() == null ? Certificate.PENDING : d.certificate();
        this.onPortal = d.onPortal();
        this.revenueRemarks = d.revenueRemarks();
        this.popCharges = d.popCharges();
        this.sharePercent = d.sharePercent();
        BigDecimal rev = d.popCharges() != null && d.sharePercent() != null
                ? d.popCharges().multiply(d.sharePercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : d.revenue() == null ? BigDecimal.ZERO : d.revenue();
        this.revenue = rev.setScale(2, RoundingMode.HALF_UP);
        this.gstPercent = gstPercent;
        this.gst = revenue.multiply(gstPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        this.invoice = revenue.add(gst);
        this.receivedOn = d.receivedOn();
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

    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String pop,
                          LocalDate investmentDate, BigDecimal amount, boolean welcomeMail, boolean investmentMail, Certificate certificate,
                          boolean onPortal, String revenueRemarks, BigDecimal popCharges, BigDecimal sharePercent, BigDecimal revenue,
                          LocalDate receivedOn, String remarks) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getPop() { return pop; }
    public LocalDate getInvestmentDate() { return investmentDate; }
    public BigDecimal getAmount() { return amount; }
    public boolean isWelcomeMail() { return welcomeMail; }
    public boolean isInvestmentMail() { return investmentMail; }
    public Certificate getCertificate() { return certificate; }
    public boolean isOnPortal() { return onPortal; }
    public String getRevenueRemarks() { return revenueRemarks; }
    public BigDecimal getPopCharges() { return popCharges; }
    public BigDecimal getSharePercent() { return sharePercent; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getInvoice() { return invoice; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
