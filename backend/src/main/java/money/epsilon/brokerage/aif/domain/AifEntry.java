package money.epsilon.brokerage.aif.domain;

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
import money.epsilon.brokerage.intermediary.service.PlatformBrokerage;
import money.epsilon.brokerage.platform.security.CurrentUser;

/** One AIF commitment booked through Epsilon, as entered on the AIF screen. Its drawdowns are {@link AifInstalment}s. */
@Entity
@Table(name = "aif_entry")
public class AifEntry {

    public enum Certificate { PENDING, RECEIVED, NOT_APPLICABLE }

    public enum PaymentStatus { PENDING, RECEIVED, RECEIVED_GST_PENDING }

    public enum Status { ACTIVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate month;
    @Column(name = "rm_id") private Long rmId;
    @Column(name = "partner_id") private Long partnerId;
    @Column(name = "sharing_percent") private BigDecimal sharingPercent;
    @Column(name = "client_id") private Long clientId;
    private String mobile;
    private String email;
    private String address;
    @Column(name = "intermediary_id") private Long platformId;
    @Column(name = "fund_name") private String fundName;
    private BigDecimal commitment;
    @Column(name = "agreement_date") private LocalDate agreementDate;
    private String tenure;
    @Column(name = "maturity_date") private LocalDate maturityDate;
    @Column(name = "soa_received") private boolean soaReceived;
    @Column(name = "application_form") private boolean applicationForm;
    @Column(name = "investment_mail") private boolean investmentMail;
    private BigDecimal quantity;
    private BigDecimal price;
    @Enumerated(EnumType.STRING) private Certificate certificate;
    @Column(name = "payout_terms") private String payoutTerms;
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
    @Column(name = "partner_paid") private boolean partnerPaid;
    @Column(name = "on_investwell") private boolean onInvestwell;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected AifEntry() {
    }

    public AifEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.paymentStatus = PaymentStatus.PENDING;
        this.certificate = Certificate.PENDING;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Terms in force for the deal's month: the platform's (R-75) and the AIF GST % (R-71). */
    public record Terms(boolean gstInclusive, BigDecimal ourShare, BigDecimal gstPercent) {
    }

    /** Sets every entered field and works out the revenue on the commitment (R-78). */
    public void fill(Details d, Terms t) {
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.sharingPercent = d.partnerId() == null ? null : d.sharingPercent();
        this.mobile = d.mobile();
        this.email = d.email();
        this.address = d.address();
        this.platformId = d.platformId();
        this.fundName = d.fundName();
        this.commitment = d.commitment();
        this.agreementDate = d.agreementDate();
        this.tenure = d.tenure();
        this.maturityDate = d.maturityDate();
        this.soaReceived = d.soaReceived();
        this.applicationForm = d.applicationForm();
        this.investmentMail = d.investmentMail();
        this.quantity = d.quantity();
        this.price = d.price();
        this.certificate = d.certificate() == null ? Certificate.PENDING : d.certificate();
        this.payoutTerms = d.payoutTerms();
        this.markupPercent = d.markupPercent();
        this.ourShare = t.ourShare();
        this.gstInclusive = t.gstInclusive();
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        this.gstPercent = t.gstPercent();
        PlatformBrokerage r = PlatformBrokerage.of(commitment, markupPercent, ourShare, gstInclusive, gstPercent, tdsApplied, tdsPercent);
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
        this.partnerPaid = d.partnerPaid();
        this.onInvestwell = d.onInvestwell();
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

    /** One drawdown as the screen sends it. */
    public record Instalment(BigDecimal amount, LocalDate paidOn) {
    }

    /** What the screen sends; revenue is worked out from it. */
    public record Details(LocalDate month, long rmId, Long partnerId, BigDecimal sharingPercent, String mobile, String email, String address,
                          long platformId, String fundName, BigDecimal commitment, LocalDate agreementDate, String tenure,
                          LocalDate maturityDate, boolean soaReceived, boolean applicationForm, boolean investmentMail,
                          BigDecimal quantity, BigDecimal price, Certificate certificate, String payoutTerms, BigDecimal markupPercent,
                          boolean tdsApplied, BigDecimal tdsPercent, boolean invoiceRaised, LocalDate invoiceDate,
                          LocalDate paymentReceivedOn, PaymentStatus paymentStatus, boolean partnerPaid, boolean onInvestwell,
                          String remarks, java.util.List<Instalment> instalments, money.epsilon.brokerage.tax.service.GivenFigures given) {
    }

    public Long getId() { return id; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public BigDecimal getSharingPercent() { return sharingPercent; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public Long getPlatformId() { return platformId; }
    public String getFundName() { return fundName; }
    public BigDecimal getCommitment() { return commitment; }
    public LocalDate getAgreementDate() { return agreementDate; }
    public String getTenure() { return tenure; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public boolean isSoaReceived() { return soaReceived; }
    public boolean isApplicationForm() { return applicationForm; }
    public boolean isInvestmentMail() { return investmentMail; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public Certificate getCertificate() { return certificate; }
    public String getPayoutTerms() { return payoutTerms; }
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
    public boolean isPartnerPaid() { return partnerPaid; }
    public boolean isOnInvestwell() { return onInvestwell; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
