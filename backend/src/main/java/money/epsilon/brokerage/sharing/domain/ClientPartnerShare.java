package money.epsilon.brokerage.sharing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.platform.dated.DatedRow;

/**
 * From validFrom to validTo, {@code partnerId} (acting as {@code partnerRole}) receives {@code share} (a fraction) for
 * the client's product. A client has at most one partner per role at any date: e.g. a sub-broker and a referral partner.
 */
@Entity
@Table(name = "client_partner_share")
public class ClientPartnerShare extends DatedRow {

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_role")
    private PartnerType partnerRole;

    @Column(name = "product_type")
    private String productType;

    private BigDecimal share;

    protected ClientPartnerShare() {
    }

    public ClientPartnerShare(Long clientId, Long partnerId, PartnerType partnerRole, String productType, BigDecimal share,
                              LocalDate from, String reason) {
        super(from, null, reason);
        this.clientId = clientId;
        this.partnerId = partnerId;
        this.partnerRole = partnerRole;
        this.productType = productType;
        this.share = share;
    }

    public Long getClientId() { return clientId; }
    public Long getPartnerId() { return partnerId; }
    public PartnerType getPartnerRole() { return partnerRole; }
    public String getProductType() { return productType; }
    public BigDecimal getShare() { return share; }

    /** A correction of who / what % from this row's first day (audited by the caller). Same role only. */
    public void correct(Long partnerId, BigDecimal share, String reason) {
        this.partnerId = partnerId;
        this.share = share;
        setReason(reason);
    }
}
