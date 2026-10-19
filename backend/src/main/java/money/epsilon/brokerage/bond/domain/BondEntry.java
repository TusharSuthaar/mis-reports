package money.epsilon.brokerage.bond.domain;

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

/** One bond deal booked through Epsilon, as entered on the Bonds screen. */
@Entity
@Table(name = "bond_entry")
public class BondEntry {

    public enum PayoutFrequency { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUMULATIVE, AT_MATURITY }

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
    @Column(name = "intermediary_id") private Long platformId;
    @Column(name = "bond_name") private String bondName;
    private String isin;
    @Column(name = "coupon_percent") private BigDecimal couponPercent;
    @Column(name = "ytm_percent") private BigDecimal ytmPercent;
    @Enumerated(EnumType.STRING) @Column(name = "payout_frequency") private PayoutFrequency payoutFrequency;
    @Column(name = "payment_schedule") private String paymentSchedule;
    @Column(name = "investment_date") private LocalDate investmentDate;
    @Column(name = "allotment_date") private LocalDate allotmentDate;
    @Column(name = "maturity_date") private LocalDate maturityDate;
    private BigDecimal quantity;
    @Column(name = "rate_per_unit") private BigDecimal ratePerUnit;
    @Column(name = "face_value") private BigDecimal faceValue;
    @Column(name = "investment_amount") private BigDecimal investmentAmount;
    @Column(name = "accrued_interest") private BigDecimal accruedInterest;
    private BigDecimal consideration;
    @Column(name = "markup_percent") private BigDecimal markupPercent;
    @Column(name = "our_share") private BigDecimal ourShare;
    @Column(name = "gst_inclusive") private boolean gstInclusive;
    private BigDecimal brokerage;
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
    @Column(name = "invoice_date") private LocalDate invoiceDate;
    @Column(name = "payment_received_on") private LocalDate paymentReceivedOn;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status") private PaymentStatus paymentStatus;
    @Column(name = "on_portal") private boolean onPortal;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected BondEntry() {
    }

    public BondEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Terms in force for the deal's month: the platform's (R-75) and the product's GST % (R-71). */
    public record Terms(boolean gstInclusive, BigDecimal ourShare, BigDecimal gstPercent) {
    }

    /** Sets every entered field and works out consideration and revenue (R-76). */
    public void fill(Details d, Terms t) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.platformId = d.platformId();
        this.bondName = d.bondName();
        this.isin = d.isin();
        this.couponPercent = d.couponPercent();
        this.ytmPercent = d.ytmPercent();
        this.payoutFrequency = d.payoutFrequency();
        this.paymentSchedule = d.paymentSchedule();
        this.investmentDate = d.investmentDate();
        this.allotmentDate = d.allotmentDate();
        this.maturityDate = d.maturityDate();
        this.quantity = d.quantity();
        this.ratePerUnit = d.ratePerUnit();
        this.faceValue = d.faceValue();
        this.investmentAmount = d.investmentAmount();
        this.accruedInterest = d.accruedInterest() == null ? BigDecimal.ZERO : d.accruedInterest();
        this.consideration = investmentAmount.add(accruedInterest);
        this.markupPercent = d.markupPercent();
        this.ourShare = t.ourShare();
        this.gstInclusive = t.gstInclusive();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = t.gstPercent();
        BondRevenue r = BondRevenue.of(investmentAmount, markupPercent, ourShare, gstInclusive, gstPercent, tdsApplied, tdsPercent);
        this.brokerage = r.brokerage();
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
        this.invoiceRaised = d.invoiceRaised();
        this.invoiceDate = d.invoiceDate();
        this.paymentReceivedOn = d.paymentReceivedOn();
        this.paymentStatus = d.paymentStatus() == null ? PaymentStatus.PENDING : d.paymentStatus();
        this.onPortal = d.onPortal();
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

    /** What the screen sends; consideration and revenue are worked out from it. */
    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, long platformId,
                          String bondName, String isin, BigDecimal couponPercent, BigDecimal ytmPercent, PayoutFrequency payoutFrequency,
                          String paymentSchedule, LocalDate investmentDate, LocalDate allotmentDate, LocalDate maturityDate,
                          BigDecimal quantity, BigDecimal ratePerUnit, BigDecimal faceValue, BigDecimal investmentAmount,
                          BigDecimal accruedInterest, BigDecimal markupPercent, boolean tdsApplied, BigDecimal tdsPercent,
                          boolean invoiceRaised, LocalDate invoiceDate, LocalDate paymentReceivedOn, PaymentStatus paymentStatus,
                          boolean onPortal, String remarks, money.epsilon.brokerage.tax.service.GivenFigures given) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public Long getPlatformId() { return platformId; }
    public String getBondName() { return bondName; }
    public String getIsin() { return isin; }
    public BigDecimal getCouponPercent() { return couponPercent; }
    public BigDecimal getYtmPercent() { return ytmPercent; }
    public PayoutFrequency getPayoutFrequency() { return payoutFrequency; }
    public String getPaymentSchedule() { return paymentSchedule; }
    public LocalDate getInvestmentDate() { return investmentDate; }
    public LocalDate getAllotmentDate() { return allotmentDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRatePerUnit() { return ratePerUnit; }
    public BigDecimal getFaceValue() { return faceValue; }
    public BigDecimal getInvestmentAmount() { return investmentAmount; }
    public BigDecimal getAccruedInterest() { return accruedInterest; }
    public BigDecimal getConsideration() { return consideration; }
    public BigDecimal getMarkupPercent() { return markupPercent; }
    public BigDecimal getOurShare() { return ourShare; }
    public boolean isGstInclusive() { return gstInclusive; }
    public BigDecimal getBrokerage() { return brokerage; }
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
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getPaymentReceivedOn() { return paymentReceivedOn; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public boolean isOnPortal() { return onPortal; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
