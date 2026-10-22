package money.epsilon.brokerage.reporting.client;

import java.util.List;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.reporting.client.ClientBrokerageService.MonthRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The client page's brokerage tab (R-09). */
@RestController
class ClientBrokerageController {

    private final ClientBrokerageService service;

    ClientBrokerageController(ClientBrokerageService service) {
        this.service = service;
    }

    @GetMapping("/api/clients/{id}/brokerage")
    @PreAuthorize(Roles.MASTER_READ)
    List<MonthRow> brokerage(@PathVariable long id, @RequestParam(defaultValue = "MF") String product) {
        if (!List.of("MF", "FD", "BOND", "AIF", "LOAN", "GIFT", "EQUITY").contains(product)) throw new IllegalArgumentException("Unknown product " + product);
        return service.months(id, product);
    }
}
