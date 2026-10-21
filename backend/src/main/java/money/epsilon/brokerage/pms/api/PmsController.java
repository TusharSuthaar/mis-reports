package money.epsilon.brokerage.pms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.pms.domain.PmsInvestment;
import money.epsilon.brokerage.pms.domain.PmsPayout;
import money.epsilon.brokerage.pms.service.PmsService;
import money.epsilon.brokerage.pms.service.PmsService.InvestmentView;
import money.epsilon.brokerage.pms.service.PmsService.PayoutView;
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

/** The PMS screen (R-85): client investments and AMC payouts. */
@RestController
@RequestMapping("/api/pms")
class PmsController {

    private final PmsService service;

    PmsController(PmsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<InvestmentView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                              @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                              @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String amc,
                              @RequestParam(required = false) String payment,
                              @RequestParam(defaultValue = "false") boolean includeCancelled) {
        // No revenue on PMS entries yet: a payment filter (shared with the other product lists) matches none of them.
        if (payment != null && !payment.isBlank()) return List.of();
        return service.search(new PmsService.Filter(from, to, q, rmId, partnerId, amc, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    InvestmentView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/suggestions")
    @PreAuthorize(Roles.MASTER_READ)
    PmsService.Suggestions suggestions() {
        return service.suggestions();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String amc) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PMS_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new PmsService.Filter(from, to, q, rmId, partnerId, amc, false)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    InvestmentView create(@Valid @RequestBody InvestmentRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    InvestmentView update(@PathVariable long id, @Valid @RequestBody InvestmentRequest req) {
        return service.update(id, req.details());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        service.cancel(id, req.reason());
    }

    @GetMapping("/payouts")
    @PreAuthorize(Roles.MASTER_READ)
    List<PayoutView> payouts(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                             @RequestParam(required = false) String amc, @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return service.payouts(from, to, amc, includeCancelled);
    }

    @PostMapping("/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    PayoutView createPayout(@Valid @RequestBody PayoutRequest req) {
        return service.createPayout(req.details());
    }

    @PutMapping("/payouts/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    PayoutView updatePayout(@PathVariable long id, @Valid @RequestBody PayoutRequest req) {
        return service.updatePayout(id, req.details());
    }

    @PostMapping("/payouts/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancelPayout(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        service.cancelPayout(id, req.reason());
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record InvestmentRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                             @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                             @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                             @Size(max = 200) String amc, @Size(max = 300) String strategy, BigDecimal amount, LocalDate investmentDate,
                             Boolean onPortal, BigDecimal trailPercent, PmsInvestment.PayoutFrequency payoutFrequency, BigDecimal qty,
                             BigDecimal price, BigDecimal consideration, PmsInvestment.Certificate certificate, Boolean investmentMail,
                             PmsInvestment.Holding holdingStatus, LocalDate redeemedOn, @Size(max = 1000) String remarks) {
        PmsInvestment.Details details() {
            return new PmsInvestment.Details(month, rmId, partnerId, mobile, email, address, amc, strategy, amount, investmentDate,
                    Boolean.TRUE.equals(onPortal), trailPercent, payoutFrequency, qty, price, consideration, certificate,
                    Boolean.TRUE.equals(investmentMail), holdingStatus, redeemedOn, remarks);
        }
    }

    record PayoutRequest(@Size(max = 200) String amc, LocalDate periodFrom, LocalDate periodTo, BigDecimal amount, Boolean tdsApplied,
                         BigDecimal tdsPercent, Boolean invoiceRaised, LocalDate receivedOn, @Size(max = 1000) String remarks) {
        PmsPayout.Details details() {
            return new PmsPayout.Details(amc, periodFrom, periodTo, amount, Boolean.TRUE.equals(tdsApplied), tdsPercent,
                    Boolean.TRUE.equals(invoiceRaised), receivedOn, remarks);
        }
    }
}
