package money.epsilon.brokerage.p2p.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.p2p.domain.P2pEntry;
import money.epsilon.brokerage.p2p.service.P2pService;
import money.epsilon.brokerage.p2p.service.P2pService.P2pView;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The P2P screen (R-87). */
@RestController
@RequestMapping("/api/p2p")
class P2pController {

    private final P2pService service;

    P2pController(P2pService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<P2pView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                       @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                       @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String platform,
                       @RequestParam(required = false) String payment,
                       @RequestParam(defaultValue = "false") boolean includeCancelled) {
        Boolean received = received(payment);
        if (payment != null && !payment.isBlank() && received == null) return List.of();
        return service.search(new P2pService.Filter(from, to, q, rmId, partnerId, platform, received, includeCancelled));
    }

    /** The shared payment filter: Received = brokerage received; Pending = brokerage not received yet; anything else matches none. */
    private static Boolean received(String payment) {
        if (payment == null || payment.isBlank()) return null;
        return switch (payment) {
            case "RECEIVED" -> Boolean.TRUE;
            case "PENDING" -> Boolean.FALSE;
            default -> null;
        };
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    P2pView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    P2pService.Suggestions suggestions() {
        return service.suggestions();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String platform) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"P2P_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new P2pService.Filter(from, to, q, rmId, partnerId, platform, null, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    P2pView create(@Valid @RequestBody P2pRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    P2pView update(@PathVariable long id, @Valid @RequestBody P2pRequest req) {
        return service.update(id, req.details());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        service.cancel(id, req.reason());
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record P2pRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                      @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                      @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                      @Size(max = 200) String platform, @Size(max = 100) String scheme, BigDecimal roiPercent, BigDecimal amount,
                      LocalDate fundsInitiatedOn, LocalDate startDate, LocalDate quarterEndDate, Boolean onPortal, BigDecimal brokerage,
                      Boolean tdsApplied, BigDecimal tdsPercent, @Size(max = 500) String paymentNote, LocalDate receivedOn,
                      @Size(max = 500) String revSharingStatus, @Size(max = 1000) String remarks) {
        P2pEntry.Details details() {
            return new P2pEntry.Details(month, rmId, partnerId, mobile, email, address, platform, scheme, roiPercent, amount, fundsInitiatedOn,
                    startDate, quarterEndDate, Boolean.TRUE.equals(onPortal), brokerage, Boolean.TRUE.equals(tdsApplied), tdsPercent, paymentNote,
                    receivedOn, revSharingStatus, remarks);
        }
    }
}
