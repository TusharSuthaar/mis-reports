package money.epsilon.brokerage.pms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.pms.service.PmsFeeService;
import money.epsilon.brokerage.pms.service.PmsFeeService.FeeView;
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

/** The PMS screen's monthly revenue per client (R-85): typed for the month the AMC's payout is received. */
@RestController
@RequestMapping("/api/pms/fees")
class PmsFeeController {

    private final PmsFeeService service;

    PmsFeeController(PmsFeeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<FeeView> list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                       @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return service.list(from, to, includeCancelled);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.MASTER_WRITE)
    FeeView create(@Valid @RequestBody FeeRequest req) {
        return service.create(req.investmentId(), req.month(), req.amount(), req.remarks());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MASTER_WRITE)
    FeeView update(@PathVariable long id, @Valid @RequestBody FeeRequest req) {
        return service.update(id, req.month(), req.amount(), req.remarks());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        service.cancel(id, req.reason());
    }

    record FeeRequest(@NotNull Long investmentId, @NotNull LocalDate month, BigDecimal amount, @Size(max = 1000) String remarks) {
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }
}
