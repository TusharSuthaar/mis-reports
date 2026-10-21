package money.epsilon.brokerage.loan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.loan.domain.LoanEntry;
import money.epsilon.brokerage.loan.domain.LoanEntry.Details;
import money.epsilon.brokerage.loan.service.LoanService;
import money.epsilon.brokerage.loan.service.LoanService.LoanView;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.tax.service.GivenFigures;
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

/** The Loans screen: payouts across months, add, edit, cancel, Excel. */
@RestController
@RequestMapping("/api/loans")
class LoanController {

    private final LoanService service;

    LoanController(LoanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<LoanView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                        @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                        @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String lender,
                        @RequestParam(required = false) String payment,
                        @RequestParam(defaultValue = "false") boolean includeCancelled) {
        if (unknownPayment(payment)) return List.of();
        return service.search(new LoanService.Filter(from, to, q, rmId, partnerId, lender, paymentStatus(payment), includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    LoanView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    LoanService.Suggestions suggestions() {
        return service.suggestions();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String lender,
                                  @RequestParam(required = false) String payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Loans_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new LoanService.Filter(from, to, q, rmId, partnerId, lender,
                        unknownPayment(payment) ? null : paymentStatus(payment), false)));
    }

    /**
     * The filters are shared with the other product lists (All products tab): a payment status loans do not have (e.g.
     * "Received – GST pending") matches no loan instead of being refused.
     */
    private static boolean unknownPayment(String payment) {
        return payment != null && !payment.isBlank() && paymentStatus(payment) == null;
    }

    private static LoanEntry.PaymentStatus paymentStatus(String payment) {
        if (payment == null || payment.isBlank()) return null;
        for (LoanEntry.PaymentStatus p : LoanEntry.PaymentStatus.values()) if (p.name().equals(payment)) return p;
        return null;
    }

    @GetMapping("/alerts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<String, Long> alerts() {
        return Map.of("pendingPayments", service.pendingPayments());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    LoanView create(@Valid @RequestBody LoanRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    LoanView update(@PathVariable long id, @Valid @RequestBody LoanRequest req) {
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
    record LoanRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                       @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                       @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                       @Size(max = 200) String lender, @Size(max = 100) String loanType, LocalDate disbursalDate,
                       BigDecimal sanctionedAmount, BigDecimal utilisedAmount, BigDecimal ratePercent,
                       LoanEntry.PayoutKind payoutKind, LoanEntry.PayoutOn payoutOn, BigDecimal payoutPercent, BigDecimal fixedAmount,
                       Boolean tdsApplied, BigDecimal tdsPercent, Boolean invoiceRaised, LoanEntry.PaymentStatus paymentStatus,
                       @Size(max = 500) String paymentNote, LocalDate receivedOn, Boolean loanClosed, @Size(max = 1000) String remarks,
                       Boolean figuresAsGiven, BigDecimal givenGross, BigDecimal givenTds, BigDecimal givenGst, BigDecimal givenInvoice) {
        Details details() {
            return new Details(month, rmId, partnerId, mobile, email, address, lender, loanType, disbursalDate, sanctionedAmount,
                    utilisedAmount, ratePercent, payoutKind, payoutOn, payoutPercent, fixedAmount, Boolean.TRUE.equals(tdsApplied), tdsPercent,
                    Boolean.TRUE.equals(invoiceRaised), paymentStatus, paymentNote, receivedOn, Boolean.TRUE.equals(loanClosed), remarks,
                    Boolean.TRUE.equals(figuresAsGiven) ? new GivenFigures(givenGross, givenTds, givenGst, givenInvoice) : null);
        }
    }
}
