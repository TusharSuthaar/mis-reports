package money.epsilon.brokerage.bond.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.bond.domain.BondEntry;
import money.epsilon.brokerage.bond.domain.BondEntry.Details;
import money.epsilon.brokerage.bond.service.BondService;
import money.epsilon.brokerage.bond.service.BondService.BondView;
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

/** The Bonds screen: deals per month, add, edit, cancel, Excel. */
@RestController
@RequestMapping("/api/bonds")
class BondController {

    private final BondService service;

    BondController(BondService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<BondView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                        @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                        @RequestParam(required = false) Long partnerId, @RequestParam(required = false) Long platformId,
                        @RequestParam(required = false) BondEntry.PaymentStatus payment,
                        @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return service.search(new BondService.Filter(from, to, q, rmId, partnerId, platformId, payment, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    BondView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    BondService.Suggestions suggestions() {
        return service.suggestions();
    }

    @GetMapping("/alerts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<String, Long> alerts() {
        return Map.of("pendingPayments", service.pendingPayments());
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) Long platformId,
                                  @RequestParam(required = false) BondEntry.PaymentStatus payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"BONDS_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new BondService.Filter(from, to, q, rmId, partnerId, platformId, payment, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    BondView create(@Valid @RequestBody BondRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    BondView update(@PathVariable long id, @Valid @RequestBody BondRequest req) {
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
    record BondRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                       @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                       @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                       @NotNull Long platformId, @Size(max = 300) String bondName, @Size(max = 40) String isin,
                       BigDecimal couponPercent, BigDecimal ytmPercent, BondEntry.PayoutFrequency payoutFrequency,
                       @Size(max = 300) String paymentSchedule, LocalDate investmentDate, LocalDate allotmentDate, LocalDate maturityDate,
                       BigDecimal quantity, BigDecimal ratePerUnit, BigDecimal faceValue, BigDecimal investmentAmount,
                       BigDecimal accruedInterest, BigDecimal markupPercent, Boolean tdsApplied, BigDecimal tdsPercent,
                       Boolean invoiceRaised, LocalDate invoiceDate, LocalDate paymentReceivedOn, BondEntry.PaymentStatus paymentStatus,
                       Boolean onPortal, @Size(max = 1000) String remarks, Boolean figuresAsGiven, BigDecimal givenGross, BigDecimal givenTds, BigDecimal givenGst, BigDecimal givenInvoice) {
        Details details() {
            return new Details(month, rmId, partnerId, mobile, email, address, platformId, bondName, isin, couponPercent, ytmPercent,
                    payoutFrequency, paymentSchedule, investmentDate, allotmentDate, maturityDate, quantity, ratePerUnit, faceValue,
                    investmentAmount, accruedInterest, markupPercent, Boolean.TRUE.equals(tdsApplied), tdsPercent,
                    Boolean.TRUE.equals(invoiceRaised), invoiceDate, paymentReceivedOn, paymentStatus, Boolean.TRUE.equals(onPortal), remarks,
                    Boolean.TRUE.equals(figuresAsGiven) ? new money.epsilon.brokerage.tax.service.GivenFigures(givenGross, givenTds, givenGst, givenInvoice) : null);
        }
    }
}
