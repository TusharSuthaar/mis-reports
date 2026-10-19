package money.epsilon.brokerage.org.service;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.domain.RdAssignment;
import money.epsilon.brokerage.org.repository.RdAssignmentRepository;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An RM leaves and someone takes over (R-61), in one step, all or nothing: the leaver exits on {@code lastDay}; from the
 * next day the replacement — an existing person or a new RM made in the leaver's branch — has their clients and partners
 * (through {@link EmployeeExited}) and, if asked, their RD regions.
 */
@Service
public class RmHandoverService {

    private final EmployeeService employees;
    private final RegionService regions;
    private final RdAssignmentRepository rds;

    RmHandoverService(EmployeeService employees, RegionService regions, RdAssignmentRepository rds) {
        this.employees = employees;
        this.regions = regions;
        this.rds = rds;
    }

    /** {@code newRmName}: make a new RM (in the leaver's branch) instead of picking {@code replacementId}. */
    public record Handover(LocalDate lastDay, Long replacementId, String newRmName, String newRmCode, boolean takeOverRd, String reason) {
    }

    public record Result(EmployeeView leaver, EmployeeView replacement, List<Long> rdRegionIds) {
    }

    @Transactional
    public Result handOver(long leaverId, Handover h) {
        if (h.lastDay() == null) throw new IllegalArgumentException("Last working day is required");
        EmployeeView leaver = employees.get(leaverId);
        LocalDate from = h.lastDay().plusDays(1);
        String newName = Text.clean(h.newRmName());
        if ((newName == null) == (h.replacementId() == null)) throw new IllegalArgumentException("Pick who takes over, or give the new RM's name");

        EmployeeView next;
        if (newName != null) {
            String code = Text.clean(h.newRmCode());
            next = leaver.branchId() == null
                    ? employees.create(code, newName, EmployeeType.RM, from)
                    : employees.createInBranch(code, newName, EmployeeType.RM, leaver.branchId(), from);
        } else {
            next = employees.get(h.replacementId());
        }

        List<Long> rdRegions = rds.findByEmployeeIdAndValidToIsNull(leaverId).stream().map(RdAssignment::getRegionId).toList();
        boolean rdMoves = h.takeOverRd() && !rdRegions.isEmpty();
        if (rdMoves && next.type() != EmployeeType.RM) {
            throw new RuleViolationException("Only an RM can be an RD — " + next.name() + " cannot take over the RD role");
        }

        EmployeeView exited = employees.exit(leaverId, h.lastDay(), h.reason(), next.id());
        if (rdMoves) {
            for (Long region : rdRegions) regions.setRd(region, next.id(), from, h.reason());
        }
        return new Result(exited, employees.get(next.id()), rdMoves ? rdRegions : List.of());
    }
}
