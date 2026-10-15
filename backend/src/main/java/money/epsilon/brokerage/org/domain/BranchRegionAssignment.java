package money.epsilon.brokerage.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** Which region a branch sits in, over time. */
@Entity
@Table(name = "branch_region_assignment")
public class BranchRegionAssignment extends DatedRow {

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "region_id")
    private Long regionId;

    protected BranchRegionAssignment() {
    }

    public BranchRegionAssignment(Long branchId, Long regionId, LocalDate from, String reason) {
        super(from, null, reason);
        this.branchId = branchId;
        this.regionId = regionId;
    }

    public Long getBranchId() { return branchId; }
    public Long getRegionId() { return regionId; }
}
