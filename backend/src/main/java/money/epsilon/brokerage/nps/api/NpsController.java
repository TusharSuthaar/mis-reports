package money.epsilon.brokerage.nps.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.nps.domain.NpsEntry;
import money.epsilon.brokerage.nps.service.NpsService;
import money.epsilon.brokerage.nps.service.NpsService.NpsView;
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

/** The NPS screen (R-86). */
@RestController
@RequestMapping("/api/nps")
class NpsController {

    private final NpsService service;

    NpsController(NpsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<NpsView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                       @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                       @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String pop,
                       @RequestParam(required = false) String payment,
                       @RequestParam(defaultValue = "false") boolean includeCancelled) {
        Boolean received = received(payment);
        if (payment != null && !payment.isBlank() && received == null) return List.of();
        return service.search(new NpsService.Filter(from, to, q, rmId, partnerId, pop, received, includeCancelled));
    }

    /** The shared payment filter: Received = amount received; Pending = revenue not received yet; anything else matches none. */
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
    NpsView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/pops")
    @PreAuthorize(Roles.MASTER_READ)
    List<String> pops() {
        return service.pops();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String pop) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"NPS_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new NpsService.Filter(from, to, q, rmId, partnerId, pop, null, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    NpsView create(@Valid @RequestBody NpsRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    NpsView update(@PathVariable long id, @Valid @RequestBody NpsRequest req) {
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

    record NpsRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                      @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                      @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                      @Size(max = 200) String pop, LocalDate investmentDate, BigDecimal amount, Boolean welcomeMail, Boolean investmentMail,
                      NpsEntry.Certificate certificate, Boolean onPortal, @Size(max = 500) String revenueRemarks, BigDecimal popCharges,
                      BigDecimal sharePercent, BigDecimal revenue, LocalDate receivedOn, @Size(max = 1000) String remarks) {
        NpsEntry.Details details() {
            return new NpsEntry.Details(month, rmId, partnerId, mobile, email, address, pop, investmentDate, amount,
                    Boolean.TRUE.equals(welcomeMail), Boolean.TRUE.equals(investmentMail), certificate, Boolean.TRUE.equals(onPortal),
                    revenueRemarks, popCharges, sharePercent, revenue, receivedOn, remarks);
        }
    }
}
