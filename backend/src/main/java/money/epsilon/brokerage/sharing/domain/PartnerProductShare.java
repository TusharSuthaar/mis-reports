package money.epsilon.brokerage.sharing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** From validFrom to validTo, the partner's % (a fraction of net) for one product. One row per partner and product at a time. */
@Entity
@Table(name = "partner_product_share")
public class PartnerProductShare extends DatedRow {

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "product_type")
    private String productType;

    private BigDecimal share;

    protected PartnerProductShare() {
    }

    public PartnerProductShare(Long partnerId, String productType, BigDecimal share, LocalDate from, String reason) {
        super(from, null, reason);
        this.partnerId = partnerId;
        this.productType = productType;
        this.share = share;
    }

    public Long getPartnerId() { return partnerId; }
    public String getProductType() { return productType; }
    public BigDecimal getShare() { return share; }

    /** A correction of the % from this row's first day (audited by the caller). */
    public void correct(BigDecimal share, String reason) {
        this.share = share;
        setReason(reason);
    }
}
