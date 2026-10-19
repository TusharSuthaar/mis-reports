package money.epsilon.brokerage.org.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import money.epsilon.brokerage.org.domain.Branch;
import money.epsilon.brokerage.org.domain.Employee;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.domain.RdAssignment;
import money.epsilon.brokerage.org.domain.RmBranchAssignment;
import money.epsilon.brokerage.org.repository.BranchRepository;
import money.epsilon.brokerage.org.repository.EmployeeRepository;
import money.epsilon.brokerage.org.repository.RdAssignmentRepository;
import money.epsilon.brokerage.org.repository.RmBranchAssignmentRepository;
import money.epsilon.brokerage.org.service.OrgViews.AssignmentAudit;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.dated.DatedHistory;
import money.epsilon.brokerage.platform.dated.DatedRow;
import money.epsilon.brokerage.platform.dated.Timeline;
import money.epsilon.brokerage.platform.text.Text;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Employees (RMs and HOUSE books), their branch over time, and exits. */
@Service
public class EmployeeService {

    static final String AUDIT_EMPLOYEE = "EMPLOYEE";
    static final String AUDIT_BRANCH = "RM_BRANCH";

    private final EmployeeRepository employees;
    private final BranchRepository branches;
    private final RmBranchAssignmentRepository rmBranches;
    private final RdAssignmentRepository rds;
    private final AuditService audit;
    private final ChangeWindow window;
    private final ApplicationEventPublisher events;

    EmployeeService(EmployeeRepository employees, BranchRepository branches, RmBranchAssignmentRepository rmBranches,
                    RdAssignmentRepository rds, AuditService audit, ApplicationEventPublisher events, ChangeWindow window) {
        this.employees = employees;
        this.branches = branches;
        this.rmBranches = rmBranches;
        this.rds = rds;
        this.audit = audit;
        this.events = events;
        this.window = window;
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        Map<Long, Branch> branchById = branches.findAll().stream().collect(Collectors.toMap(Branch::getId, Function.identity()));
        return employees.findAll().stream()
                .sorted(Comparator.comparing(e -> e.getName().toLowerCase()))
                .map(e -> view(e, branchById))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView get(long id) {
        return view(find(id), null);
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeView> findByCode(String empCode) {
        return employees.findByEmpCodeIgnoreCase(Text.required(empCode, "Emp code")).map(e -> view(e, null));
    }

    /** For other modules: the employee (RM or HOUSE book), who must be employed on {@code day}. */
    @Transactional(readOnly = true)
    public EmployeeView requireEmployedOn(long id, LocalDate day) {
        Employee e = find(id);
        if (!e.employedOn(day)) throw new RuleViolationException(e.getName() + " is not employed on " + day.format(Timeline.DISPLAY));
        return view(e, null);
    }

    /** For other modules: the employee, who must be an RM (not a HOUSE book) employed on {@code day}. */
    @Transactional(readOnly = true)
    public EmployeeView requireRmEmployedOn(long id, LocalDate day) {
        Employee e = find(id);
        if (e.getType() != EmployeeType.RM) throw new RuleViolationException(e.getName() + " is not an RM");
        if (!e.employedOn(day)) throw new RuleViolationException(e.getName() + " is not employed on " + day.format(Timeline.DISPLAY));
        return view(e, null);
    }

    @Transactional
    /** A blank {@code empCode} gets a generated one (RM-0001, RM-0002, ...). */
    public EmployeeView create(String empCode, String name, EmployeeType type, LocalDate joinedOn) {
        String code = Text.clean(empCode);
        if (code == null) code = nextGeneratedCode();
        if (employees.findByEmpCodeIgnoreCase(code).isPresent()) {
            throw new RuleViolationException("Emp code '" + code + "' already exists");
        }
        if (type == null) throw new IllegalArgumentException("Type is required (RM or HOUSE)");
        Employee e = employees.save(new Employee(code, Text.required(name, "Name"), type, joinedOn));
        EmployeeView view = view(e, null);
        audit.record(AUDIT_EMPLOYEE, e.getId(), "CREATE", null, view);
        return view;
    }

    /** Creates an RM (or house book) and places it in a branch from {@code from}, all or nothing. */
    @Transactional
    public EmployeeView createInBranch(String empCode, String name, EmployeeType type, long branchId, LocalDate from) {
        EmployeeView created = create(empCode, name, type, null);
        assignBranch(created.id(), branchId, from, null);
        return get(created.id());
    }

    private String nextGeneratedCode() {
        for (long n = employees.count() + 1; ; n++) {
            String code = "RM-%04d".formatted(n);
            if (employees.findByEmpCodeIgnoreCase(code).isEmpty()) return code;
        }
    }

    /** Null arguments mean "leave unchanged". The emp code never changes. */
    @Transactional
    public EmployeeView update(long id, String name, EmployeeType type, LocalDate joinedOn) {
        Employee e = find(id);
        EmployeeView before = view(e, null);
        if (name != null) e.setName(Text.required(name, "Name"));
        if (type != null && type != e.getType()) {
            if (type == EmployeeType.HOUSE && rds.findFirstByEmployeeIdOrderByValidFromDesc(id).isPresent()) {
                throw new RuleViolationException(e.getName() + " has RD history and must stay an RM");
            }
            e.setType(type);
        }
        if (joinedOn != null && !joinedOn.equals(e.getJoinedOn())) {
            if (e.getExitedOn() != null && e.getExitedOn().isBefore(joinedOn)) {
                throw new RuleViolationException("Joining date cannot be after the exit date");
            }
            Optional<LocalDate> firstAssignment = rmBranches.findFirstByEmployeeIdOrderByValidFromAsc(id).map(DatedRow::getValidFrom);
            if (firstAssignment.filter(joinedOn::isAfter).isPresent()) {
                throw new RuleViolationException("Joining date cannot be after the first branch assignment ("
                        + firstAssignment.get().format(Timeline.DISPLAY) + ")");
            }
            e.setJoinedOn(joinedOn);
        }
        EmployeeView after = view(e, null);
        if (!after.equals(before)) audit.record(AUDIT_EMPLOYEE, id, "UPDATE", before, after);
        return after;
    }

    /**
     * Records the exit: closes the open branch and RD rows on {@code lastDay} and tells other modules
     * ({@link EmployeeExited}). Client reassignment is separate.
     */
    @Transactional
    public EmployeeView exit(long id, LocalDate lastDay, String reason) {
        return exit(id, lastDay, reason, null);
    }

    /**
     * {@link #exit(long, LocalDate, String)}, and {@code replacedBy} takes over their clients (and partners, if an RM)
     * from the next day (R-61). The replacement must be employed on that day.
     */
    @Transactional
    public EmployeeView exit(long id, LocalDate lastDay, String reason, Long replacedBy) {
        Employee e = find(id);
        if (e.getExitedOn() != null) throw new RuleViolationException(e.getName() + " already exited on " + e.getExitedOn().format(Timeline.DISPLAY));
        if (lastDay == null) throw new IllegalArgumentException("Exit date is required");
        if (e.getJoinedOn() != null && lastDay.isBefore(e.getJoinedOn())) throw new RuleViolationException("Exit date is before the joining date");
        String replacementName = null;
        if (replacedBy != null) {
            if (replacedBy == id) throw new RuleViolationException("Pick someone else to take over");
            Employee next = find(replacedBy);
            if (!next.employedOn(lastDay.plusDays(1))) {
                throw new RuleViolationException(next.getName() + " is not employed on " + lastDay.plusDays(1).format(Timeline.DISPLAY));
            }
            replacementName = next.getName();
        }

        EmployeeView before = view(e, null);
        closeOnExit(rmBranches.findFirstByEmployeeIdOrderByValidFromDesc(id), lastDay, rmBranches, AUDIT_BRANCH, this::branchAudit, reason, "branch");
        for (RdAssignment rd : rds.findByEmployeeIdAndValidToIsNull(id)) {
            closeOnExit(Optional.of(rd), lastDay, rds, RegionService.AUDIT_RD,
                    a -> AssignmentAudit.of(a.getRegionId(), a.getEmployeeId(), a), reason, "RD");
        }
        e.exit(lastDay);
        employees.saveAndFlush(e);
        events.publishEvent(new EmployeeExited(id, e.getName(), lastDay, reason, replacedBy));
        EmployeeView after = view(e, null);
        audit.record(AUDIT_EMPLOYEE, id, "EXIT", before, new ExitAudit(after, reason, replacementName));
        return after;
    }

    /** Puts the employee in {@code branchId} from {@code from}, closing the previous branch row. */
    @Transactional
    public void assignBranch(long id, long branchId, LocalDate from, String reason) {
        Employee e = find(id);
        if (!branches.existsById(branchId)) throw new NotFoundException("Branch " + branchId + " not found");
        window.beforeChange(from);
        if (!e.employedOn(from)) throw new RuleViolationException(e.getName() + " is not employed on " + from.format(Timeline.DISPLAY));
        Optional<RmBranchAssignment> latest = rmBranches.findFirstByEmployeeIdOrderByValidFromDesc(id);
        if (latest.filter(a -> a.getValidTo() == null && a.getBranchId().equals(branchId)).isPresent()) {
            throw new RuleViolationException(e.getName() + " is already in this branch");
        }
        DatedHistory.makeRoom(latest, from, rmBranches, audit, AUDIT_BRANCH, this::branchAudit, reason);
        DatedHistory.add(new RmBranchAssignment(id, branchId, from, e.getExitedOn(), reason), rmBranches, audit, AUDIT_BRANCH, this::branchAudit);
    }

    @Transactional
    public void endBranch(long id, LocalDate lastDay, String reason) {
        RmBranchAssignment open = rmBranches.findFirstByEmployeeIdOrderByValidFromDesc(id).filter(a -> a.getValidTo() == null)
                .orElseThrow(() -> new RuleViolationException("This employee has no current branch"));
        window.beforeChange(lastDay.plusDays(1));
        DatedHistory.end(open, lastDay, rmBranches, audit, AUDIT_BRANCH, this::branchAudit, reason);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> branchHistory(long id) {
        find(id);
        return rmBranches.findByEmployeeIdOrderByValidFromDesc(id).stream()
                .map(a -> HistoryEntry.of(a, a.getBranchId(), branches.findById(a.getBranchId()).map(Branch::getName).orElse("?")))
                .toList();
    }

    private <T extends DatedRow> void closeOnExit(Optional<T> latest, LocalDate lastDay,
                                                  org.springframework.data.jpa.repository.JpaRepository<T, Long> repo,
                                                  String entity, Function<T, ?> view, String reason, String what) {
        if (latest.isEmpty()) return;
        T row = latest.get();
        if (row.getValidFrom().isAfter(lastDay)) {
            throw new RuleViolationException("There is a " + what + " assignment starting " + row.getValidFrom().format(Timeline.DISPLAY)
                    + ", after the exit date");
        }
        if (row.getValidTo() == null || row.getValidTo().isAfter(lastDay)) {
            Object before = view.apply(row);
            row.closeOn(lastDay);
            repo.saveAndFlush(row);
            audit.record(entity, row.getId(), "CLOSE", before, new DatedHistory.Closed(view.apply(row), reason));
        }
    }

    private AssignmentAudit branchAudit(RmBranchAssignment a) {
        return AssignmentAudit.of(a.getEmployeeId(), a.getBranchId(), a);
    }

    private Employee find(long id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    private EmployeeView view(Employee e, Map<Long, Branch> branchById) {
        Branch branch = rmBranches.findFirstByEmployeeIdOrderByValidFromDesc(e.getId())
                .filter(a -> a.getValidTo() == null || e.getExitedOn() != null && a.getValidTo().equals(e.getExitedOn()))
                .map(a -> branchById != null ? branchById.get(a.getBranchId()) : branches.findById(a.getBranchId()).orElse(null))
                .orElse(null);
        return new EmployeeView(e.getId(), e.getEmpCode(), e.getName(), e.getType(), e.getStatus(), e.getJoinedOn(),
                e.getExitedOn(), branch == null ? null : branch.getId(), branch == null ? null : branch.getName());
    }

    public record ExitAudit(EmployeeView employee, String reason, String replacedBy) {
    }
}
