package money.epsilon.brokerage.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.dated.DatedRow;

/** Which branch an employee (RM or HOUSE) belongs to, over time. */
@Entity
@Table(name = "rm_branch_assignment")
public class RmBranchAssignment extends DatedRow {

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "branch_id")
    private Long branchId;

    protected RmBranchAssignment() {
    }

    public RmBranchAssignment(Long employeeId, Long branchId, LocalDate from, LocalDate to, String reason) {
        super(from, to, reason);
        this.employeeId = employeeId;
        this.branchId = branchId;
    }

    public Long getEmployeeId() { return employeeId; }
    public Long getBranchId() { return branchId; }
}
