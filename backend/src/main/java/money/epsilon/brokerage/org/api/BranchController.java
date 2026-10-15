package money.epsilon.brokerage.org.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.org.service.BranchService;
import money.epsilon.brokerage.org.service.OrgViews.BranchView;
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
@RequestMapping("/api/branches")
class BranchController {

    private final BranchService service;

    BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<BranchView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    BranchView create(@Valid @RequestBody CreateBranchRequest req) {
        return service.create(req.name(), req.regionId(), req.from(), req.reason());
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    BranchView rename(@PathVariable long id, @Valid @RequestBody RenameRequest req) {
        return service.rename(id, req.name());
    }

    @GetMapping("/{id}/region")
    @PreAuthorize(Roles.MASTER_READ)
    List<HistoryEntry> regionHistory(@PathVariable long id) {
        return service.regionHistory(id);
    }

    @PostMapping("/{id}/region")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void moveToRegion(@PathVariable long id, @Valid @RequestBody MoveRequest req) {
        service.moveToRegion(id, req.regionId(), req.from(), req.reason());
    }

    record CreateBranchRequest(@NotBlank @Size(max = 100) String name, @NotNull Long regionId, @NotNull LocalDate from,
                               @Size(max = 500) String reason) {
    }

    record RenameRequest(@NotBlank @Size(max = 100) String name) {
    }

    record MoveRequest(@NotNull Long regionId, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }
}
