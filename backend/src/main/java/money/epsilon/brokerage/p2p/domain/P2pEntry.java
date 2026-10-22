package money.epsilon.brokerage.p2p.domain;

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

/** One P2P investment, as entered on the P2P screen (a row of the P2P register, R-87). */
@Entity
@Table(name = "p2p_entry")
public class P2pEntry {

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
    private String platform;
    private String scheme;
    @Column(name = "roi_percent") private BigDecimal roiPercent;
    private BigDecimal amount;
    @Column(name = "funds_initiated_on") private LocalDate fundsInitiatedOn;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "quarter_end_date") private LocalDate quarterEndDate;
    @Column(name = "on_portal") private boolean onPortal;
    private BigDecimal brokerage;
    private BigDecimal gross;
    @Column(name = "tds_applied") private boolean tdsApplied;
    @Column(name = "tds_percent") private BigDecimal tdsPercent;
    private BigDecimal tds;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal net;
    private BigDecimal invoice;
    @Column(name = "payment_note") private String paymentNote;
    @Column(name = "received_on") private LocalDate receivedOn;
    @Column(name = "rev_sharing_status") private String revSharingStatus;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected P2pEntry() {
    }

    public P2pEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /**
     * Sets every entered field (R-87). From the typed brokerage (excl. GST; none yet = 0): TDS = brokerage × TDS % when ticked;
     * GST = brokerage × the P2P GST %; net = brokerage − TDS; invoice = brokerage + GST. Rounded to paise.
     */
    public void fill(Details d, BigDecimal gstPercent) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.platform = d.platform();
        this.scheme = d.scheme();
        this.roiPercent = d.roiPercent();
        this.amount = d.amount();
        this.fundsInitiatedOn = d.fundsInitiatedOn();
        this.startDate = d.startDate();
        this.quarterEndDate = d.quarterEndDate();
        this.onPortal = d.onPortal();
        this.brokerage = d.brokerage();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = gstPercent;
        this.gross = (brokerage == null ? BigDecimal.ZERO : brokerage).setScale(2, RoundingMode.HALF_UP);
        this.tds = tdsApplied ? pct(gross, tdsPercent) : BigDecimal.ZERO.setScale(2);
        this.gst = pct(gross, gstPercent);
        this.net = gross.subtract(tds);
        this.invoice = gross.add(gst);
        this.paymentNote = d.paymentNote();
        this.receivedOn = d.receivedOn();
        this.revSharingStatus = d.revSharingStatus();
        this.remarks = d.remarks();
        touch();
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        touch();
    }

    private void touch() {
        this.updatedBy = CurrentUser.username();
        this.updatedAt = Instant.now();
    }

    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String platform,
                          String scheme, BigDecimal roiPercent, BigDecimal amount, LocalDate fundsInitiatedOn, LocalDate startDate,
                          LocalDate quarterEndDate, boolean onPortal, BigDecimal brokerage, boolean tdsApplied, BigDecimal tdsPercent,
                          String paymentNote, LocalDate receivedOn, String revSharingStatus, String remarks) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getPlatform() { return platform; }
    public String getScheme() { return scheme; }
    public BigDecimal getRoiPercent() { return roiPercent; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getFundsInitiatedOn() { return fundsInitiatedOn; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getQuarterEndDate() { return quarterEndDate; }
    public boolean isOnPortal() { return onPortal; }
    public BigDecimal getBrokerage() { return brokerage; }
    public BigDecimal getGross() { return gross; }
    public boolean isTdsApplied() { return tdsApplied; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getNet() { return net; }
    public BigDecimal getInvoice() { return invoice; }
    public String getPaymentNote() { return paymentNote; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getRevSharingStatus() { return revSharingStatus; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
