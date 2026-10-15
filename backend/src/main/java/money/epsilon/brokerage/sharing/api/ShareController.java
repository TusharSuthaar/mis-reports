package money.epsilon.brokerage.sharing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.partner.domain.PartnerType;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.sharing.service.PartnerMergeService;
import money.epsilon.brokerage.sharing.service.PartnerMergeService.MergeResult;
import money.epsilon.brokerage.sharing.service.ShareService;
import money.epsilon.brokerage.sharing.service.ShareService.ShareView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShareController {

    private static final int MAX_IDS = 500;

    private final ShareService service;
    private final PartnerMergeService merges;

    ShareController(ShareService service, PartnerMergeService merges) {
        this.service = service;
        this.merges = merges;
    }

    /** Merge a duplicate partner (another spelling of the same person) into the right one. */
    @PostMapping("/api/partners/{id}/merge")
    @PreAuthorize(Roles.MASTER_WRITE)
    MergeResult merge(@PathVariable long id, @Valid @RequestBody MergeRequest req) {
        return merges.merge(id, req.intoId(), req.reason());
    }

    /** Current partner shares (sub-broker first) for each of the given clients (missing = no partner). */
    @GetMapping("/api/shares")
    @PreAuthorize(Roles.MASTER_READ)
    Map<Long, List<ShareView>> current(@RequestParam List<Long> clientIds) {
        if (clientIds.size() > MAX_IDS) throw new IllegalArgumentException("At most " + MAX_IDS + " clients at a time");
        return service.current(clientIds);
    }

    @GetMapping("/api/shares/partner-counts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<Long, Long> partnerCounts() {
        return service.clientCountsByPartner();
    }

    @GetMapping("/api/clients/{id}/partner")
    @PreAuthorize(Roles.MASTER_READ)
    List<ShareView> history(@PathVariable long id) {
        return service.history(id);
    }

    @PostMapping("/api/clients/{id}/partner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void set(@PathVariable long id, @Valid @RequestBody SetShareRequest req) {
        service.set(id, req.partnerId(), req.percent(), req.from(), req.reason());
    }

    @PostMapping("/api/clients/{id}/partner/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void end(@PathVariable long id, @Valid @RequestBody EndShareRequest req) {
        service.end(id, req.role(), req.lastDay(), req.reason());
    }

    record SetShareRequest(@NotNull Long partnerId, @NotNull BigDecimal percent, @NotNull LocalDate from, @Size(max = 500) String reason) {
    }

    record MergeRequest(@NotNull Long intoId, @Size(max = 500) String reason) {
    }

    record EndShareRequest(@NotNull PartnerType role, @NotNull LocalDate lastDay, @Size(max = 500) String reason) {
    }
}
