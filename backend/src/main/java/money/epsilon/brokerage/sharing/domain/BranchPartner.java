package money.epsilon.brokerage.sharing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** From validFrom to validTo, {@code partnerId} shares in every client of the branch, before the client's own partners. */
@Entity
@Table(name = "branch_partner")
public class BranchPartner extends DatedRow {

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "partner_id")
    private Long partnerId;

    protected BranchPartner() {
    }

    public BranchPartner(Long branchId, Long partnerId, LocalDate from, String reason) {
        super(from, null, reason);
        this.branchId = branchId;
        this.partnerId = partnerId;
    }

    public Long getBranchId() { return branchId; }
    public Long getPartnerId() { return partnerId; }

    /** A correction of the partner from this row's first day (audited by the caller). */
    public void correct(Long partnerId, String reason) {
        this.partnerId = partnerId;
        setReason(reason);
    }
}
