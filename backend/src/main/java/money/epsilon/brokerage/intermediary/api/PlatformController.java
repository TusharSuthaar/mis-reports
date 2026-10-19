package money.epsilon.brokerage.intermediary.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.intermediary.service.IntermediaryService;
import money.epsilon.brokerage.intermediary.service.IntermediaryService.PlatformView;
import money.epsilon.brokerage.intermediary.service.IntermediaryService.TermsView;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Setup → Platforms. */
@RestController
@RequestMapping("/api/platforms")
class PlatformController {

    private final IntermediaryService service;

    PlatformController(IntermediaryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<PlatformView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    PlatformView create(@Valid @RequestBody NameRequest req) {
        return service.create(req.name());
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    PlatformView rename(@PathVariable long id, @Valid @RequestBody NameRequest req) {
        return service.rename(id, req.name());
    }

    @GetMapping("/{id}/terms")
    @PreAuthorize(Roles.MASTER_READ)
    List<TermsView> history(@PathVariable long id) {
        return service.history(id);
    }

    @GetMapping("/{id}/terms/on")
    @PreAuthorize(Roles.MASTER_READ)
    TermsView on(@PathVariable long id, @RequestParam LocalDate date) {
        return service.on(id, date);
    }

    @PostMapping("/{id}/terms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void setTerms(@PathVariable long id, @Valid @RequestBody TermsRequest req) {
        service.setTerms(id, Boolean.TRUE.equals(req.gstInclusive()), req.tdsPercent(), req.ourShare(), req.from(), req.reason());
    }

    record NameRequest(@NotBlank @Size(max = 200) String name) {
    }

    record TermsRequest(Boolean gstInclusive, @NotNull BigDecimal tdsPercent, @NotNull BigDecimal ourShare, @NotNull LocalDate from,
                        @Size(max = 500) String reason) {
    }
}
