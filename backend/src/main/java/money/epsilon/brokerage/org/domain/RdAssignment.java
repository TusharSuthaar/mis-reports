package money.epsilon.brokerage.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** The RD of a region, over time. One RD per region and one region per RD at any date. */
@Entity
@Table(name = "rd_assignment")
public class RdAssignment extends DatedRow {

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "employee_id")
    private Long employeeId;

    protected RdAssignment() {
    }

    public RdAssignment(Long regionId, Long employeeId, LocalDate from, LocalDate to, String reason) {
        super(from, to, reason);
        this.regionId = regionId;
        this.employeeId = employeeId;
    }

    public Long getRegionId() { return regionId; }
    public Long getEmployeeId() { return employeeId; }
}
