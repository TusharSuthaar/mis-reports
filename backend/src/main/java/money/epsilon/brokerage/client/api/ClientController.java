package money.epsilon.brokerage.client.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.client.service.ClientService.ClientDetail;
import money.epsilon.brokerage.client.service.ClientService.ClientView;
import money.epsilon.brokerage.org.service.OrgViews.HistoryEntry;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
class ClientController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ClientService service;

    ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    PageResponse<ClientView> list(@RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "false") boolean needsRm,
                                  @RequestParam(required = false) Long rmId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return service.page(q, needsRm, rmId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    ClientDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/rm")
    @PreAuthorize(Roles.MASTER_READ)
    List<HistoryEntry> rmHistory(@PathVariable long id) {
        return service.rmHistory(id);
    }

    /** Change one client's RM from a date. */
    @PostMapping("/{id}/rm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void changeRm(@PathVariable long id, @Valid @RequestBody ChangeRmRequest req) {
        service.changeRm(id, req.rmId(), req.from(), req.reason());
    }

    /** Correct a client's PAN (audited; refused if another client has it). */
    @PostMapping("/{id}/pan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void changePan(@PathVariable long id, @Valid @RequestBody ChangePanRequest req) {
        service.changePan(id, req.pan(), req.reason(), Boolean.TRUE.equals(req.takeFromOther()));
    }

    /** Change the RM of several clients from the same date, all or none. */
    @PostMapping("/rm")
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Integer> changeRmForAll(@Valid @RequestBody BulkChangeRmRequest req) {
        return Map.of("changed", service.changeRmForAll(req.clientIds(), req.rmId(), req.from(), req.reason()));
    }

    /** {@code takeFromOther}: move the PAN from another client record that has no RM (a duplicate). */
    record ChangePanRequest(@NotBlank @Size(max = 20) String pan, @Size(max = 500) String reason, Boolean takeFromOther) {
    }

    record ChangeRmRequest(@NotNull Long rmId, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }

    record BulkChangeRmRequest(@NotEmpty @Size(max = 5000) List<Long> clientIds, @NotNull Long rmId, @NotNull LocalDate from,
                               @Size(max = 500) String reason) {
    }
}
