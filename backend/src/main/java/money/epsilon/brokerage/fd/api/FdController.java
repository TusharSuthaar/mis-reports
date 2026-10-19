package money.epsilon.brokerage.fd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.fd.domain.FdEntry;
import money.epsilon.brokerage.fd.domain.FdEntry.Details;
import money.epsilon.brokerage.fd.service.FdService;
import money.epsilon.brokerage.fd.service.FdService.FdView;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
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

/** The FD screen: entries per month, add, edit, cancel. */
@RestController
@RequestMapping("/api/fd")
class FdController {

    private final FdService service;

    FdController(FdService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<FdView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                      @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                      @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String platform,
                      @RequestParam(required = false) FdEntry.PaymentStatus payment,
                      @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return service.search(new FdService.Filter(from, to, q, rmId, partnerId, platform, payment, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    FdView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    FdService.Suggestions suggestions() {
        return service.suggestions();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    org.springframework.http.ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                                           @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                                           @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String platform,
                                                           @RequestParam(required = false) FdEntry.PaymentStatus payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"FD_" + name + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new FdService.Filter(from, to, q, rmId, partnerId, platform, payment, false)));
    }

    @GetMapping("/alerts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<String, Long> alerts() {
        return Map.of("pendingPayments", service.pendingPayments());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    FdView create(@Valid @RequestBody FdRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    FdView update(@PathVariable long id, @Valid @RequestBody FdRequest req) {
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

    /** Client: {@code clientId} of an existing client, or a new client's name and PAN (add only). */
    record FdRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                     @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                     @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                     @Size(max = 200) String fdName, @Size(max = 60) String fdNo, FdEntry.InterestFrequency interestFrequency,
                     @Size(max = 200) String platform, LocalDate depositDate, Integer periodMonths, LocalDate maturityDate, BigDecimal ratePercent,
                     BigDecimal amount, FdEntry.Compounding compounding, FdEntry.Certificate certificate, BigDecimal marginPercent,
                     Boolean tdsApplied, BigDecimal tdsPercent, Boolean invoiceRaised, Boolean onPortal,
                     FdEntry.PaymentStatus paymentStatus,
                     @Size(max = 1000) String remarks, Boolean figuresAsGiven, BigDecimal givenGross, BigDecimal givenTds, BigDecimal givenGst, BigDecimal givenInvoice) {
        Details details() {
            return new Details(month, rmId, partnerId, mobile, email, address, fdName, fdNo, interestFrequency, platform, depositDate,
                    periodMonths == null ? 0 : periodMonths, maturityDate, ratePercent, amount, compounding, certificate, marginPercent, Boolean.TRUE.equals(tdsApplied),
                    tdsPercent, Boolean.TRUE.equals(invoiceRaised),
                    Boolean.TRUE.equals(onPortal), paymentStatus, remarks,
                    Boolean.TRUE.equals(figuresAsGiven) ? new money.epsilon.brokerage.tax.service.GivenFigures(givenGross, givenTds, givenGst, givenInvoice) : null);
        }
    }
}
