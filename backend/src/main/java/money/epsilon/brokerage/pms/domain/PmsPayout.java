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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/**
 * One AMC payout for an invoice period (R-85), as kept below the PMS register: amount excl. GST; TDS = amount × TDS % when
 * ticked; GST = amount × the PMS GST %; net = amount − TDS; invoice = amount + GST. Rounded to paise.
 */
@Entity
@Table(name = "pms_payout")
public class PmsPayout {

    public enum Status { ACTIVE, CANCELLED }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String amc;
    @Column(name = "period_from") private LocalDate periodFrom;
    @Column(name = "period_to") private LocalDate periodTo;
    private BigDecimal amount;
    @Column(name = "tds_applied") private boolean tdsApplied;
    @Column(name = "tds_percent") private BigDecimal tdsPercent;
    private BigDecimal tds;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal net;
    private BigDecimal invoice;
    @Column(name = "invoice_raised") private boolean invoiceRaised;
    @Column(name = "received_on") private LocalDate receivedOn;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected PmsPayout() {
    }

    /** A new payout, not yet filled. */
    public static PmsPayout create() {
        PmsPayout p = new PmsPayout();
        p.init();
        return p;
    }

    private void init() {
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    public void fill(Details d, BigDecimal gstPercent) {
        this.amc = d.amc();
        this.periodFrom = d.periodFrom();
        this.periodTo = d.periodTo() == null ? d.periodFrom() : d.periodTo();
        this.amount = d.amount().setScale(2, RoundingMode.HALF_UP);
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = gstPercent;
        this.tds = tdsApplied ? pct(amount, tdsPercent) : BigDecimal.ZERO.setScale(2);
        this.gst = pct(amount, gstPercent);
        this.net = amount.subtract(tds);
        this.invoice = amount.add(gst);
        this.invoiceRaised = d.invoiceRaised();
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

    public record Details(String amc, LocalDate periodFrom, LocalDate periodTo, BigDecimal amount, boolean tdsApplied, BigDecimal tdsPercent,
                          boolean invoiceRaised, LocalDate receivedOn, String remarks) {
    }

    public Long getId() { return id; }
    public String getAmc() { return amc; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public BigDecimal getAmount() { return amount; }
    public boolean isTdsApplied() { return tdsApplied; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getNet() { return net; }
    public BigDecimal getInvoice() { return invoice; }
    public boolean isInvoiceRaised() { return invoiceRaised; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
