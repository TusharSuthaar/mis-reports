package money.epsilon.brokerage.preipo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.security.CurrentUser;

/** A vendor's base price for a scrip on a day, and the client price it gives at our mark-up (R-92). */
@Entity
@Table(name = "preipo_price")
public class PreIpoPrice {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scrip_id") private Long scripId;
    @Column(name = "intermediary_id") private Long vendorId;
    @Column(name = "as_of") private LocalDate asOf;
    @Column(name = "base_price") private BigDecimal basePrice;
    @Column(name = "markup_percent") private BigDecimal markupPercent;
    @Column(name = "client_price") private BigDecimal clientPrice;
    @Column(name = "changed_by") private String changedBy;
    @Column(name = "changed_at") private Instant changedAt;

    protected PreIpoPrice() {
    }

    public PreIpoPrice(long scripId, long vendorId, LocalDate asOf) {
        this.scripId = scripId;
        this.vendorId = vendorId;
        this.asOf = asOf;
    }

    /** Client price = base × (1 + mark-up %), to paise. */
    public void set(BigDecimal basePrice, BigDecimal markupPercent) {
        this.basePrice = basePrice;
        this.markupPercent = markupPercent;
        this.clientPrice = basePrice.multiply(HUNDRED.add(markupPercent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        this.changedBy = CurrentUser.username();
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getScripId() { return scripId; }
    public Long getVendorId() { return vendorId; }
    public LocalDate getAsOf() { return asOf; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getMarkupPercent() { return markupPercent; }
    public BigDecimal getClientPrice() { return clientPrice; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
