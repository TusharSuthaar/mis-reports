package money.epsilon.brokerage.reporting.client;

import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.reporting.client.ClientHoldingsService.Holdings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The client page's holdings tab: everything the client's PAN holds (R-09). */
@RestController
class ClientHoldingsController {

    private final ClientHoldingsService service;

    ClientHoldingsController(ClientHoldingsService service) {
        this.service = service;
    }

    @GetMapping("/api/clients/{id}/holdings")
    @PreAuthorize(Roles.MASTER_READ)
    Holdings holdings(@PathVariable long id) {
        return service.holdings(id);
    }
}
