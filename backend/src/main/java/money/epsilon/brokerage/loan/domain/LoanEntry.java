package money.epsilon.brokerage.loan.domain;

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
import money.epsilon.brokerage.tax.service.GivenFigures;

/** One loan payout, as entered on the Loans screen (a row of the LOANS register). */
@Entity
@Table(name = "loan_entry")
public class LoanEntry {

    /** DISBURSAL: the one-time payout on a new loan; TRAIL: a repeat payout (e.g. LAS, quarterly on the utilised amount). */
    public enum PayoutKind { DISBURSAL, TRAIL }

    /** What the payout % is taken on. */
    public enum PayoutOn { SANCTIONED, UTILISED }

    public enum PaymentStatus { PENDING, RECEIVED }

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
    private String lender;
    @Column(name = "loan_type") private String loanType;
    @Column(name = "disbursal_date") private LocalDate disbursalDate;
    @Column(name = "sanctioned_amount") private BigDecimal sanctionedAmount;
    @Column(name = "utilised_amount") private BigDecimal utilisedAmount;
    @Column(name = "rate_percent") private BigDecimal ratePercent;
    @Enumerated(EnumType.STRING) @Column(name = "payout_kind") private PayoutKind payoutKind;
    @Enumerated(EnumType.STRING) @Column(name = "payout_on") private PayoutOn payoutOn;
    @Column(name = "payout_percent") private BigDecimal payoutPercent;
    @Column(name = "fixed_amount") private BigDecimal fixedAmount;
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
    @Enumerated(EnumType.STRING) @Column(name = "payment_status") private PaymentStatus paymentStatus;
    @Column(name = "payment_note") private String paymentNote;
    @Column(name = "received_on") private LocalDate receivedOn;
    @Column(name = "loan_closed") private boolean loanClosed;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected LoanEntry() {
    }

    public LoanEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Sets every entered field and works out the revenue (R-80), or keeps the register's figures (R-79). */
    public void fill(Details d, BigDecimal gstPercent) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.lender = d.lender();
        this.loanType = d.loanType();
        this.disbursalDate = d.disbursalDate();
        this.sanctionedAmount = d.sanctionedAmount();
        this.utilisedAmount = d.utilisedAmount();
        this.ratePercent = d.ratePercent();
        this.payoutKind = d.payoutKind() == null ? PayoutKind.DISBURSAL : d.payoutKind();
        this.payoutOn = d.payoutOn() == null ? PayoutOn.SANCTIONED : d.payoutOn();
        this.payoutPercent = d.payoutPercent();
        this.fixedAmount = d.fixedAmount();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = gstPercent;
        this.invoiceRaised = d.invoiceRaised();
        this.paymentStatus = d.paymentStatus() == null ? PaymentStatus.PENDING : d.paymentStatus();
        this.paymentNote = d.paymentNote();
        this.receivedOn = d.receivedOn();
        this.loanClosed = d.loanClosed();
        this.remarks = d.remarks();
        LoanRevenue r = LoanRevenue.of(base(), payoutPercent, fixedAmount, tdsApplied, tdsPercent, gstPercent);
        this.gross = r.gross();
        this.tds = r.tds();
        this.gst = r.gst();
        this.net = r.net();
        this.invoice = r.invoice();
        if (d.given() != null) {
            GivenFigures.Resolved g = d.given().resolve();
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

    /** The amount the payout % is taken on. */
    public BigDecimal base() {
        return payoutOn == PayoutOn.UTILISED ? utilisedAmount : sanctionedAmount;
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
    public record Details(LocalDate month, long rmId, Long partnerId, String mobile, String email, String address, String lender,
                          String loanType, LocalDate disbursalDate, BigDecimal sanctionedAmount, BigDecimal utilisedAmount,
                          BigDecimal ratePercent, PayoutKind payoutKind, PayoutOn payoutOn, BigDecimal payoutPercent, BigDecimal fixedAmount,
                          boolean tdsApplied, BigDecimal tdsPercent, boolean invoiceRaised, PaymentStatus paymentStatus, String paymentNote,
                          LocalDate receivedOn, boolean loanClosed, String remarks, GivenFigures given) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getLender() { return lender; }
    public String getLoanType() { return loanType; }
    public LocalDate getDisbursalDate() { return disbursalDate; }
    public BigDecimal getSanctionedAmount() { return sanctionedAmount; }
    public BigDecimal getUtilisedAmount() { return utilisedAmount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public PayoutKind getPayoutKind() { return payoutKind; }
    public PayoutOn getPayoutOn() { return payoutOn; }
    public BigDecimal getPayoutPercent() { return payoutPercent; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
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
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getPaymentNote() { return paymentNote; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public boolean isLoanClosed() { return loanClosed; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
