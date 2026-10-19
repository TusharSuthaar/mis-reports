package money.epsilon.brokerage.fd.domain;

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

/** One FD booked through Epsilon, as entered on the FD screen. */
@Entity
@Table(name = "fd_entry")
public class FdEntry {

    public enum InterestFrequency { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUMULATIVE }

    /** How a cumulative FD compounds. */
    public enum Compounding {
        QUARTERLY(4), HALF_YEARLY(2), YEARLY(1);

        private final int timesPerYear;

        Compounding(int timesPerYear) {
            this.timesPerYear = timesPerYear;
        }

        public int timesPerYear() {
            return timesPerYear;
        }
    }

    public enum Certificate { PENDING, RECEIVED, NOT_APPLICABLE }

    public enum PaymentStatus { PENDING, RECEIVED, RECEIVED_GST_PENDING }

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
    @Column(name = "fd_name") private String fdName;
    @Column(name = "fd_no") private String fdNo;
    @Enumerated(EnumType.STRING) @Column(name = "interest_frequency") private InterestFrequency interestFrequency;
    private String platform;
    @Column(name = "period_months") private int periodMonths;
    @Enumerated(EnumType.STRING) private Compounding compounding;
    @Column(name = "deposit_date") private LocalDate depositDate;
    @Column(name = "maturity_date") private LocalDate maturityDate;
    @Column(name = "rate_percent") private BigDecimal ratePercent;
    private BigDecimal amount;
    @Column(name = "maturity_value") private BigDecimal maturityValue;
    @Enumerated(EnumType.STRING) private Certificate certificate;
    @Column(name = "margin_percent") private BigDecimal marginPercent;
    private BigDecimal gross;
    @Column(name = "tds_applied") private boolean tdsApplied;
    @Column(name = "tds_percent") private BigDecimal tdsPercent;
    private BigDecimal tds;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal net;
    private BigDecimal invoice;
    @Column(name = "figures_as_given") private boolean figuresAsGiven;
    @Column(name = "invoice_raised") private boolean invoiceRaised;
    @Column(name = "on_portal") private boolean onPortal;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status") private PaymentStatus paymentStatus;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected FdEntry() {
    }

    public FdEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Sets every entered field and works out the revenue (R-70). */
    public void fill(Details d, BigDecimal gstPercent) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.fdName = d.fdName();
        this.fdNo = d.fdNo();
        this.interestFrequency = d.interestFrequency();
        this.platform = d.platform();
        this.depositDate = d.depositDate();
        this.maturityDate = d.maturityDate();
        this.ratePercent = d.ratePercent();
        this.amount = d.amount();
        this.periodMonths = d.periodMonths();
        this.compounding = d.interestFrequency() == InterestFrequency.CUMULATIVE
                ? (d.compounding() == null ? Compounding.YEARLY : d.compounding()) : null;
        this.maturityValue = FdMaturity.value(d.amount(), d.ratePercent(), periodMonths, d.interestFrequency(), compounding);
        this.certificate = d.certificate();
        this.marginPercent = d.marginPercent();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = gstPercent;
        this.invoiceRaised = d.invoiceRaised();
        this.onPortal = d.onPortal();
        this.paymentStatus = d.paymentStatus() == null ? PaymentStatus.PENDING : d.paymentStatus();
        this.remarks = d.remarks();
        FdRevenue r = FdRevenue.of(amount, marginPercent, tdsApplied, tdsPercent, gstPercent);
        this.gross = r.gross();
        this.tds = r.tds();
        this.gst = r.gst();
        this.net = r.net();
        this.invoice = r.invoice();
        if (d.given() != null) {
            // The register's own figures, kept as they are (R-79).
            var g = d.given().resolve();
            this.gross = g.gross();
            this.tds = g.tds();
            this.gst = g.gst();
            this.net = g.net();
            this.invoice = g.invoice();
            this.tdsApplied = g.tds().signum() > 0;
        }
        this.figuresAsGiven = d.given() != null;
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

    /** What the screen sends; the revenue is worked out from it. */
    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String fdName,
                          String fdNo, InterestFrequency interestFrequency, String platform, LocalDate depositDate, int periodMonths, LocalDate maturityDate,
                          BigDecimal ratePercent, BigDecimal amount, Compounding compounding, Certificate certificate,
                          BigDecimal marginPercent, boolean tdsApplied, BigDecimal tdsPercent, boolean invoiceRaised,
                          boolean onPortal, PaymentStatus paymentStatus, String remarks,
                          money.epsilon.brokerage.tax.service.GivenFigures given) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getFdName() { return fdName; }
    public String getFdNo() { return fdNo; }
    public InterestFrequency getInterestFrequency() { return interestFrequency; }
    public String getPlatform() { return platform; }
    public Compounding getCompounding() { return compounding; }
    public int getPeriodMonths() { return periodMonths; }
    public LocalDate getDepositDate() { return depositDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getMaturityValue() { return maturityValue; }
    public Certificate getCertificate() { return certificate; }
    public BigDecimal getMarginPercent() { return marginPercent; }
    public BigDecimal getGross() { return gross; }
    public boolean isTdsApplied() { return tdsApplied; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getNet() { return net; }
    public BigDecimal getInvoice() { return invoice; }
    public boolean isFiguresAsGiven() { return figuresAsGiven; }
    public boolean isInvoiceRaised() { return invoiceRaised; }
    public boolean isOnPortal() { return onPortal; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
