package money.epsilon.brokerage.partner.domain;

public enum PartnerStatus {
    /** PENDING: approval under process — linked to clients but paid nothing until ACTIVE (rule R-55). */
    ACTIVE, PENDING, INACTIVE
}
