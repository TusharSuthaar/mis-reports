package money.epsilon.brokerage.gift.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.gift.domain.GiftEntry;
import money.epsilon.brokerage.gift.domain.GiftEntry.Details;
import money.epsilon.brokerage.gift.service.GiftService;
import money.epsilon.brokerage.gift.service.GiftService.GiftView;
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

/** The GIFT CITY screen: entries across months, add, edit, cancel, Excel. */
@RestController
@RequestMapping("/api/gift")
class GiftController {

    private final GiftService service;

    GiftController(GiftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<GiftView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                        @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                        @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String fund,
                        @RequestParam(required = false) String payment,
                        @RequestParam(defaultValue = "false") boolean includeCancelled) {
        if (unknownPayment(payment)) return List.of();
        return service.search(new GiftService.Filter(from, to, q, rmId, partnerId, fund, paymentStatus(payment), includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.MASTER_READ)
    GiftView get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/funds")
    @PreAuthorize(Roles.MASTER_READ)
    List<String> funds() {
        return service.funds();
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> export(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                                  @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String fund,
                                  @RequestParam(required = false) String payment) {
        String name = from == null && to == null ? "all" : (from == null ? "" : from.toString()) + "_to_" + (to == null ? "" : to.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GIFT_CITY_" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(new GiftService.Filter(from, to, q, rmId, partnerId, fund,
                        unknownPayment(payment) ? null : paymentStatus(payment), false)));
    }

    @GetMapping("/alerts")
    @PreAuthorize(Roles.MASTER_READ)
    Map<String, Long> alerts() {
        return Map.of("pendingPayments", service.pendingPayments());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    GiftView create(@Valid @RequestBody GiftRequest req) {
        return service.create(req.clientId(), req.newClientName(), req.newClientPan(), req.details());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    GiftView update(@PathVariable long id, @Valid @RequestBody GiftRequest req) {
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

    /** The other product lists share the filters: a payment status GIFT City entries do not have matches nothing. */
    private static boolean unknownPayment(String payment) {
        return payment != null && !payment.isBlank() && paymentStatus(payment) == null;
    }

    private static GiftEntry.PaymentStatus paymentStatus(String payment) {
        if (payment == null || payment.isBlank()) return null;
        for (GiftEntry.PaymentStatus p : GiftEntry.PaymentStatus.values()) if (p.name().equals(payment)) return p;
        return null;
    }

    record GiftRequest(Long clientId, @Size(max = 300) String newClientName, @Size(max = 20) String newClientPan,
                       @NotNull LocalDate month, @NotNull Long rmId, Long partnerId,
                       @Size(max = 20) String mobile, @Size(max = 200) String email, @Size(max = 500) String address,
                       @Size(max = 300) String fund, BigDecimal amountUsd, BigDecimal fxRate, LocalDate fxRateDate,
                       @Size(max = 80) String fxSource, BigDecimal amountInr, Boolean onPortal,
                       GiftEntry.TrxnStatus trxnStatus, BigDecimal brokerage, Boolean tdsApplied, BigDecimal tdsPercent, Boolean invoiceRaised,
                       GiftEntry.PaymentStatus paymentStatus, LocalDate receivedOn, @Size(max = 1000) String remarks) {
        Details details() {
            return new Details(month, rmId, partnerId, mobile, email, address, fund, amountUsd, fxRate, fxRateDate, fxSource, amountInr, Boolean.TRUE.equals(onPortal),
                    trxnStatus, brokerage, Boolean.TRUE.equals(tdsApplied), tdsPercent, Boolean.TRUE.equals(invoiceRaised), paymentStatus,
                    receivedOn, remarks);
        }
    }
}
