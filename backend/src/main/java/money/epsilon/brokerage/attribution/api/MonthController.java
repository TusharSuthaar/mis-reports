package money.epsilon.brokerage.attribution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import money.epsilon.brokerage.attribution.service.AttributionService;
import money.epsilon.brokerage.attribution.service.AttributionService.Summary;
import money.epsilon.brokerage.attribution.service.MonthActions;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.period.service.PeriodService.PeriodView;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Month lifecycle: process (split) → lock (FINANCE) → unlock (ADMIN, with a reason). */
@RestController
class MonthController {

    private final MonthActions actions;
    private final AttributionService attribution;
    private final PeriodService periods;

    MonthController(MonthActions actions, AttributionService attribution, PeriodService periods) {
        this.actions = actions;
        this.attribution = attribution;
        this.periods = periods;
    }

    @PostMapping("/api/months/{id}/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Long> process(@PathVariable long id) {
        return Map.of("jobId", actions.startProcessing(id));
    }

    @GetMapping("/api/months/{id}/summary")
    @PreAuthorize(Roles.MASTER_READ)
    Summary summary(@PathVariable long id) {
        return attribution.summary(id);
    }

    @GetMapping("/api/months/{id}/readiness")
    @PreAuthorize(Roles.MASTER_READ)
    AttributionService.Readiness readiness(@PathVariable long id) {
        return attribution.readiness(id);
    }

    /** FINANCE locks; for now ADMIN can too (user, 28 Oct 2026), R-42. */
    @PostMapping("/api/months/{id}/lock")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    PeriodView lock(@PathVariable long id) {
        return periods.lock(id, CurrentUser.username());
    }

    @PostMapping("/api/months/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    PeriodView unlock(@PathVariable long id, @Valid @RequestBody UnlockRequest req) {
        return periods.unlock(id, req.reason());
    }

    record UnlockRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
