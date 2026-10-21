package money.epsilon.brokerage.reporting.client;

import java.time.YearMonth;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.reporting.client.MfListService.MfList;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The MF tab of Product entries (R-81). */
@RestController
class MfListController {

    private final MfListService service;

    MfListController(MfListService service) {
        this.service = service;
    }

    @GetMapping("/api/mf")
    @PreAuthorize(Roles.MASTER_READ)
    MfList list(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to,
                @RequestParam(required = false) String q, @RequestParam(required = false) Long rmId,
                @RequestParam(required = false) Long partnerId) {
        return service.search(new MfListService.Filter(from, to, q, rmId, partnerId));
    }
}
