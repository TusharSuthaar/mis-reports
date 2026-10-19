package money.epsilon.brokerage.tax.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.sharing.domain.Product;
import money.epsilon.brokerage.tax.service.ProductTaxService;
import money.epsilon.brokerage.tax.service.ProductTaxService.ProductTaxLine;
import money.epsilon.brokerage.tax.service.ProductTaxService.TaxRateView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Setup → Products &amp; taxes. */
@RestController
@RequestMapping("/api/product-taxes")
class ProductTaxController {

    private final ProductTaxService service;

    ProductTaxController(ProductTaxService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.MASTER_READ)
    List<ProductTaxLine> list() {
        return service.list();
    }

    @GetMapping("/{product}")
    @PreAuthorize(Roles.MASTER_READ)
    List<TaxRateView> history(@PathVariable Product product) {
        return service.history(product);
    }

    /** The rate on a day (an FD entry uses it for its defaults). */
    @GetMapping("/{product}/on")
    @PreAuthorize(Roles.MASTER_READ)
    TaxRateView on(@PathVariable Product product, @RequestParam LocalDate date) {
        return service.on(product, date);
    }

    @PostMapping("/{product}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void set(@PathVariable Product product, @Valid @RequestBody SetRequest req) {
        service.set(product, req.gstPercent(), req.tdsPercent(), Boolean.TRUE.equals(req.tdsByDefault()), req.from(), req.reason());
    }

    record SetRequest(@NotNull BigDecimal gstPercent, @NotNull BigDecimal tdsPercent, Boolean tdsByDefault, @NotNull LocalDate from,
                      @Size(max = 500) String reason) {
    }
}
