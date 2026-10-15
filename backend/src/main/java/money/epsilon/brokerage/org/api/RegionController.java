package money.epsilon.brokerage.org.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.org.service.OrgViews.RegionView;
import money.epsilon.brokerage.org.service.RegionService;
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
@RequestMapping("/api/regions")
class RegionController {

    private final RegionService service;

    RegionController(RegionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<RegionView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    RegionView create(@Valid @RequestBody NameRequest req) {
        return service.create(req.name());
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    RegionView rename(@PathVariable long id, @Valid @RequestBody NameRequest req) {
        return service.rename(id, req.name());
    }

    @GetMapping("/{id}/rd")
    @PreAuthorize(Roles.MASTER_READ)
    List<HistoryEntry> rdHistory(@PathVariable long id) {
        return service.rdHistory(id);
    }

    @PostMapping("/{id}/rd")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void setRd(@PathVariable long id, @Valid @RequestBody SetRdRequest req) {
        service.setRd(id, req.employeeId(), req.from(), req.reason());
    }

    @PostMapping("/{id}/rd/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void endRd(@PathVariable long id, @Valid @RequestBody EndRequest req) {
        service.endRd(id, req.lastDay(), req.reason());
    }

    record NameRequest(@NotBlank @Size(max = 100) String name) {
    }

    record SetRdRequest(@NotNull Long employeeId, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }
}
