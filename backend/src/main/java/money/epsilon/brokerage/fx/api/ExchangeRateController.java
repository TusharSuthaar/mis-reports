package money.epsilon.brokerage.fx.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.fx.service.ExchangeRateService;
import money.epsilon.brokerage.fx.service.ExchangeRateService.Rate;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exchange rates (R-84): the list on Platforms &amp; taxes, and the rate a form needs for a day. */
@RestController
@RequestMapping("/api/exchange-rates/{currency}")
class ExchangeRateController {

    private final ExchangeRateService service;

    ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<Rate> list(@PathVariable String currency) {
        return service.list(currency);
    }

    /** The day's rate: from the list, or fetched from Frankfurter (ECB) and kept. Writes on the first fetch, so it is a POST. */
    @PostMapping("/lookup")
    @PreAuthorize(Roles.MASTER_WRITE)
    Rate lookup(@PathVariable String currency, @RequestParam(required = false) LocalDate date) {
        return service.toInr(currency, date);
    }

    @PutMapping("/{day}")
    @PreAuthorize(Roles.MASTER_WRITE)
    Rate set(@PathVariable String currency, @PathVariable LocalDate day, @Valid @RequestBody SetRequest req) {
        return service.setByHand(currency, day, req.rate(), req.reason());
    }

    record SetRequest(@NotNull BigDecimal rate, @Size(max = 500) String reason) {
    }
}
