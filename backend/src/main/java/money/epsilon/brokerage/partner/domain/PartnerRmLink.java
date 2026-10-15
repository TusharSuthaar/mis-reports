package money.epsilon.brokerage.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** A partner works with an RM over a period. Partners and RMs are many-to-many. */
@Entity
@Table(name = "partner_rm_link")
public class PartnerRmLink extends DatedRow {

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "rm_id")
    private Long rmId;

    protected PartnerRmLink() {
    }

    public PartnerRmLink(Long partnerId, Long rmId, LocalDate from, LocalDate to, String reason) {
        super(from, to, reason);
        this.partnerId = partnerId;
        this.rmId = rmId;
    }

    public Long getPartnerId() { return partnerId; }

    /** After a partner merge: this link now belongs to the partner the duplicate was merged into. */
    public void moveTo(Long partnerId) { this.partnerId = partnerId; }
    public Long getRmId() { return rmId; }
}
