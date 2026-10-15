package money.epsilon.brokerage.org.api;

import java.time.LocalDate;
import money.epsilon.brokerage.org.service.OrgChartService;
import money.epsilon.brokerage.org.service.OrgChartService.OrgChart;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.time.BusinessCalendar;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrgChartController {

    private final OrgChartService service;
    private final BusinessCalendar calendar;

    OrgChartController(OrgChartService service, BusinessCalendar calendar) {
        this.service = service;
        this.calendar = calendar;
    }

    /** The org as it stood on {@code date} (default: today). */
    @GetMapping("/api/org")
    @PreAuthorize(Roles.MASTER_READ)
    OrgChart chart(@RequestParam(required = false) LocalDate date) {
        return service.asOf(date != null ? date : calendar.today());
    }
}
