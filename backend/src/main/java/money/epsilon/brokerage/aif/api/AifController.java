package money.epsilon.brokerage.aif.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.aif.domain.AifEntry;
import money.epsilon.brokerage.aif.domain.AifEntry.Details;
import money.epsilon.brokerage.aif.service.AifService;
import money.epsilon.brokerage.aif.service.AifService.AifView;
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

/** The AIF screen: commitments across months (with filters), add, edit, cancel, Excel. */
@RestController
@RequestMapping("/api/aif")
class AifController {

    private final AifService service;

    AifController(AifService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<AifView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                       @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                       @RequestParam(required = false) Long partnerId, @RequestParam(required = false) Long platformId,
                       @RequestParam(required = false) AifEntry.PaymentStatus payment,
                       @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return service.search(new AifService.Filter(from, to, q, rmId, partnerId, platformId, payment, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    AifView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    AifService.Suggestions suggestions() {
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
                                  @RequestParam(required = false) AifEntry.PaymentStatus payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AIF_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new AifService.Filter(from, to, q, rmId, partnerId, platformId, payment, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    AifView create(@Valid @RequestBody AifRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    AifView update(@PathVariable long id, @Valid @RequestBody AifRequest req) {
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

    record InstalmentRequest(BigDecimal amount, LocalDate paidOn) {
    }

    /** Client: {@code clientId} of an existing client, or a new client's name and PAN (add only). */
    record AifRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                      @NotNull LocalDate month, @NotNull Long rmId, Long partnerId, BigDecimal sharingPercent,
                      @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                      @NotNull Long platformId, @Size(max = 300) String fundName, BigDecimal commitment, LocalDate agreementDate,
                      @Size(max = 100) String tenure, LocalDate maturityDate, Boolean soaReceived, Boolean applicationForm,
                      Boolean investmentMail, BigDecimal quantity, BigDecimal price, AifEntry.Certificate certificate,
                      @Size(max = 300) String payoutTerms, BigDecimal markupPercent, Boolean tdsApplied, BigDecimal tdsPercent,
                      Boolean invoiceRaised, LocalDate invoiceDate, LocalDate paymentReceivedOn, AifEntry.PaymentStatus paymentStatus,
                      Boolean partnerPaid, Boolean onInvestwell, @Size(max = 1000) String remarks, List<InstalmentRequest> instalments,
                      Boolean figuresAsGiven, BigDecimal givenGross, BigDecimal givenTds, BigDecimal givenGst, BigDecimal givenInvoice) {
        Details details() {
            return new Details(month, rmId, partnerId, sharingPercent, mobile, email, address, platformId, fundName, commitment, agreementDate,
                    tenure, maturityDate, Boolean.TRUE.equals(soaReceived), Boolean.TRUE.equals(applicationForm), Boolean.TRUE.equals(investmentMail),
                    quantity, price, certificate, payoutTerms, markupPercent, Boolean.TRUE.equals(tdsApplied), tdsPercent,
                    Boolean.TRUE.equals(invoiceRaised), invoiceDate, paymentReceivedOn, paymentStatus, Boolean.TRUE.equals(partnerPaid),
                    Boolean.TRUE.equals(onInvestwell), remarks,
                    instalments == null ? List.of() : instalments.stream().map(i -> new AifEntry.Instalment(i.amount(), i.paidOn())).toList(),
                    Boolean.TRUE.equals(figuresAsGiven) ? new money.epsilon.brokerage.tax.service.GivenFigures(givenGross, givenTds, givenGst, givenInvoice) : null);
        }
    }
}
