package money.epsilon.brokerage.preipo.domain;

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

/** One Pre-IPO deal: a client's buy of unlisted shares, or a buyback, as entered on the Pre-IPO screen (R-93). */
@Entity
@Table(name = "preipo_entry")
public class PreIpoEntry {

    public enum Kind { BUY, BUYBACK }

    /** Where the deal stands on the client portal (the register's "EPSILON PORTAL" column). */
    public enum PortalStatus { PENDING, UPDATED, OFFLINE, NOT_TO_UPDATE, LISTED, BOUGHT_BACK, DELETED }

    /** The register's billing column: whether the vendor has been billed and has paid. */
    public enum BillingStatus { TO_CONFIRM, DEAL_DONE, BILLED, PAYMENT_RECEIVED, PAID_BY_CLIENT }

    public enum DealSheet { NA, PENDING, SHARED, SIGNED }

    public enum Status { ACTIVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) private Kind kind;
    private LocalDate month;
    @Column(name = "rm_id") private Long rmId;
    @Column(name = "partner_id") private Long partnerId;
    @Column(name = "sharing_percent") private BigDecimal sharingPercent;
    @Column(name = "client_id") private Long clientId;
    private String mobile;
    private String email;
    @Column(name = "dp_name") private String dpName;
    @Column(name = "dp_id") private String dpId;
    @Column(name = "demat_client_id") private String dematClientId;
    @Column(name = "scrip_id") private Long scripId;
    @Column(name = "intermediary_id") private Long vendorId;
    @Column(name = "deal_date") private LocalDate dealDate;
    private BigDecimal quantity;
    @Column(name = "client_price") private BigDecimal clientPrice;
    @Column(name = "vendor_price") private BigDecimal vendorPrice;
    @Column(name = "markup_per_share") private BigDecimal markupPerShare;
    @Column(name = "markup_percent") private BigDecimal markupPercent;
    private BigDecimal amount;
    private BigDecimal brokerage;
    private BigDecimal gross;
    @Column(name = "gst_percent") private BigDecimal gstPercent;
    private BigDecimal gst;
    private BigDecimal invoice;
    @Column(name = "tds_applied") private boolean tdsApplied;
    @Column(name = "tds_percent") private BigDecimal tdsPercent;
    private BigDecimal tds;
    private BigDecimal net;
    @Enumerated(EnumType.STRING) @Column(name = "portal_status") private PortalStatus portalStatus;
    @Enumerated(EnumType.STRING) @Column(name = "billing_status") private BillingStatus billingStatus;
    @Enumerated(EnumType.STRING) @Column(name = "deal_sheet") private DealSheet dealSheet;
    private String remarks;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    protected PreIpoEntry() {
    }

    public PreIpoEntry(Long clientId) {
        this.clientId = clientId;
        this.status = Status.ACTIVE;
        this.createdBy = CurrentUser.username();
        this.createdAt = Instant.now();
        touch();
    }

    /** Sets every entered field and works out mark-up and revenue (R-93); {@code gstPercent}: Pre-IPO GST of the deal's month. */
    public void fill(Details d, BigDecimal gstPercent) {
        this.kind = d.kind();
        this.month = d.month();
        this.rmId = d.rmId();
        this.partnerId = d.partnerId();
        this.sharingPercent = d.partnerId() == null ? null : d.sharingPercent();
        this.mobile = d.mobile();
        this.email = d.email();
        this.dpName = d.dpName();
        this.dpId = d.dpId();
        this.dematClientId = d.dematClientId();
        this.scripId = d.scripId();
        this.vendorId = d.vendorId();
        this.dealDate = d.dealDate();
        this.quantity = d.quantity();
        this.clientPrice = d.clientPrice();
        this.vendorPrice = d.vendorPrice();
        this.gstPercent = gstPercent;
        this.tdsApplied = d.tdsApplied();
        this.tdsPercent = d.tdsApplied() ? d.tdsPercent() : BigDecimal.ZERO;
        PreIpoRevenue r = PreIpoRevenue.of(kind, quantity, clientPrice, vendorPrice, gstPercent, tdsApplied, tdsPercent);
        this.markupPerShare = r.markupPerShare();
        this.markupPercent = r.markupPercent();
        this.amount = r.amount();
        this.brokerage = r.brokerage();
        this.gross = r.gross();
        this.gst = r.gst();
        this.invoice = r.invoice();
        this.tds = r.tds();
        this.net = r.net();
        this.portalStatus = d.portalStatus() == null ? PortalStatus.PENDING : d.portalStatus();
        this.billingStatus = d.billingStatus() == null ? BillingStatus.TO_CONFIRM : d.billingStatus();
        this.dealSheet = d.dealSheet() == null ? DealSheet.NA : d.dealSheet();
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

    /** What the screen sends; mark-up and revenue are worked out from it. */
    public record Details(Kind kind, LocalDate month, long rmId, Long partnerId, BigDecimal sharingPercent, String mobile, String email,
                          String dpName, String dpId, String dematClientId, long scripId, long vendorId, LocalDate dealDate,
                          BigDecimal quantity, BigDecimal clientPrice, BigDecimal vendorPrice, boolean tdsApplied, BigDecimal tdsPercent,
                          PortalStatus portalStatus, BillingStatus billingStatus, DealSheet dealSheet, String remarks) {
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public LocalDate getMonth() { return month; }
    public Long getRmId() { return rmId; }
    public Long getPartnerId() { return partnerId; }
    public BigDecimal getSharingPercent() { return sharingPercent; }
    public Long getClientId() { return clientId; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getDpName() { return dpName; }
    public String getDpId() { return dpId; }
    public String getDematClientId() { return dematClientId; }
    public Long getScripId() { return scripId; }
    public Long getVendorId() { return vendorId; }
    public LocalDate getDealDate() { return dealDate; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getClientPrice() { return clientPrice; }
    public BigDecimal getVendorPrice() { return vendorPrice; }
    public BigDecimal getMarkupPerShare() { return markupPerShare; }
    public BigDecimal getMarkupPercent() { return markupPercent; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBrokerage() { return brokerage; }
    public BigDecimal getGross() { return gross; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public BigDecimal getGst() { return gst; }
    public BigDecimal getInvoice() { return invoice; }
    public boolean isTdsApplied() { return tdsApplied; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getNet() { return net; }
    public PortalStatus getPortalStatus() { return portalStatus; }
    public BillingStatus getBillingStatus() { return billingStatus; }
    public DealSheet getDealSheet() { return dealSheet; }
    public String getRemarks() { return remarks; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
