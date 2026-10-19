package money.epsilon.brokerage.org.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.org.domain.EmployeeType;
import money.epsilon.brokerage.org.service.EmployeeService;
import money.epsilon.brokerage.org.service.RmHandoverService;
import money.epsilon.brokerage.org.service.OrgViews.EmployeeView;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private final EmployeeService service;
    private final RmHandoverService handover;

    EmployeeController(EmployeeService service, RmHandoverService handover) {
        this.handover = handover;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<EmployeeView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    EmployeeView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    EmployeeView create(@Valid @RequestBody CreateEmployeeRequest req) {
        if (req.branchId() != null) {
            if (req.from() == null) throw new IllegalArgumentException("From date is required with a branch");
            return service.createInBranch(req.empCode(), req.name(), req.type(), req.branchId(), req.from());
        }
        return service.create(req.empCode(), req.name(), req.type(), req.joinedOn());
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    EmployeeView update(@PathVariable long id, @Valid @RequestBody UpdateEmployeeRequest req) {
        return service.update(id, req.name(), req.type(), req.joinedOn());
    }

    @PostMapping("/{id}/exit")
    @PreAuthorize(Roles.MASTER_WRITE)
    EmployeeView exit(@PathVariable long id, @Valid @RequestBody EndRequest req) {
        return service.exit(id, req.lastDay(), req.reason());
    }

    /** The RM leaves and someone takes over their clients, partners and (if asked) RD regions from the next day (R-61). */
    @PostMapping("/{id}/hand-over")
    @PreAuthorize(Roles.MASTER_WRITE)
    RmHandoverService.Result handOver(@PathVariable long id, @Valid @RequestBody HandoverRequest req) {
        return handover.handOver(id, new RmHandoverService.Handover(req.lastDay(), req.replacementId(), req.newRmName(), req.newRmCode(),
                !Boolean.FALSE.equals(req.takeOverRd()), req.reason()));
    }

    record HandoverRequest(@NotNull LocalDate lastDay, Long replacementId,
                           @Size(max = 200) String newRmName,
                           @Size(max = 50) String newRmCode, Boolean takeOverRd,
                           @Size(max = 500) String reason) {
    }

    @GetMapping("/{id}/branch")
    @PreAuthorize(Roles.MASTER_READ)
    List<HistoryEntry> branchHistory(@PathVariable long id) {
        return service.branchHistory(id);
    }

    @PostMapping("/{id}/branch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void assignBranch(@PathVariable long id, @Valid @RequestBody AssignBranchRequest req) {
        service.assignBranch(id, req.branchId(), req.from(), req.reason());
    }

    @PostMapping("/{id}/branch/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void endBranch(@PathVariable long id, @Valid @RequestBody EndRequest req) {
        service.endBranch(id, req.lastDay(), req.reason());
    }

    record CreateEmployeeRequest(@Size(max = 30) String empCode, @NotBlank @Size(max = 200) String name,
                                 @NotNull EmployeeType type, LocalDate joinedOn, Long branchId, LocalDate from) {
    }

    record UpdateEmployeeRequest(@Size(min = 1, max = 200) String name, EmployeeType type, LocalDate joinedOn) {
    }

    record AssignBranchRequest(@NotNull Long branchId, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }
}
