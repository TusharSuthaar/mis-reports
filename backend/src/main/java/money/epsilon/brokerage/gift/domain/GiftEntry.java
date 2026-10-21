package money.epsilon.brokerage.gift.domain;

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

/** One GIFT City investment, as entered on the GIFT CITY screen (a row of the GIFT CITY register, R-82). */
@Entity
@Table(name = "gift_entry")
public class GiftEntry {

    public enum TrxnStatus { PENDING, DONE }

    public enum PaymentStatus { PENDING, RECEIVED }

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
    private String fund;
    @Column(name = "amount_usd") private BigDecimal amountUsd;
    @Column(name = "fx_rate") private BigDecimal fxRate;
    @Column(name = "fx_rate_date") private LocalDate fxRateDate;
    @Column(name = "fx_source") private String fxSource;
    @Column(name = "amount_inr") private BigDecimal amountInr;
    @Column(name = "on_portal") private boolean onPortal;
    @Enumerated(EnumType.STRING) @Column(name = "trxn_status") private TrxnStatus trxnStatus;
    private BigDecimal brokerage;
    private BigDecimal gross;
    @Column(name = "tds_applied") private boolean tdsApplied;
    @Column(name = "tds_percent") private BigDecimal tdsPercent;
    private BigDecimal tds;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal net;
    private BigDecimal invoice;
    @Column(name = "invoice_raised") private boolean invoiceRaised;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status") private PaymentStatus paymentStatus;
    @Column(name = "received_on") private LocalDate receivedOn;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected GiftEntry() {
    }

    public GiftEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /**
     * Sets every entered field (R-82). Rupee amount = USD × rate when a rate is given, else as typed. Revenue from the typed
     * brokerage (excl. GST; none yet = 0): TDS = brokerage × TDS % when ticked; GST = brokerage × GST %; net = brokerage −
     * TDS; invoice = brokerage + GST. Rounded to paise.
     */
    public void fill(Details d, BigDecimal gstPercent) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.fund = d.fund();
        this.amountUsd = d.amountUsd();
        this.fxRate = d.fxRate();
        this.fxRateDate = d.fxRate() == null ? null : d.fxRateDate();
        this.fxSource = d.fxRate() == null ? null : d.fxSource();
        this.amountInr = d.amountUsd() != null && d.fxRate() != null ? d.amountUsd().multiply(d.fxRate()).setScale(2, RoundingMode.HALF_UP) : d.amountInr();
        this.onPortal = d.onPortal();
        this.trxnStatus = d.trxnStatus() == null ? TrxnStatus.PENDING : d.trxnStatus();
        this.brokerage = d.brokerage();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = gstPercent;
        this.gross = (brokerage == null ? BigDecimal.ZERO : brokerage).setScale(2, RoundingMode.HALF_UP);
        this.tds = tdsApplied ? pct(gross, tdsPercent) : BigDecimal.ZERO.setScale(2);
        this.gst = pct(gross, gstPercent);
        this.net = gross.subtract(tds);
        this.invoice = gross.add(gst);
        this.invoiceRaised = d.invoiceRaised();
        this.paymentStatus = d.paymentStatus() == null ? PaymentStatus.PENDING : d.paymentStatus();
        this.receivedOn = d.receivedOn();
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

    /** What the screen sends. */
    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String fund,
                          BigDecimal amountUsd, BigDecimal fxRate, LocalDate fxRateDate, String fxSource, BigDecimal amountInr, boolean onPortal, TrxnStatus trxnStatus,
                          BigDecimal brokerage, boolean tdsApplied, BigDecimal tdsPercent, boolean invoiceRaised, PaymentStatus paymentStatus,
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
    public String getFund() { return fund; }
    public BigDecimal getAmountUsd() { return amountUsd; }
    public BigDecimal getFxRate() { return fxRate; }
    public LocalDate getFxRateDate() { return fxRateDate; }
    public String getFxSource() { return fxSource; }
    public BigDecimal getAmountInr() { return amountInr; }
    public boolean isOnPortal() { return onPortal; }
    public TrxnStatus getTrxnStatus() { return trxnStatus; }
    public BigDecimal getBrokerage() { return brokerage; }
    public BigDecimal getGross() { return gross; }
    public boolean isTdsApplied() { return tdsApplied; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getNet() { return net; }
    public BigDecimal getInvoice() { return invoice; }
    public boolean isInvoiceRaised() { return invoiceRaised; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
