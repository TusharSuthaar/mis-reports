package money.epsilon.brokerage.platform.activity;

import java.time.Instant;
import money.epsilon.brokerage.platform.activity.ActivityService.ActivityFilter;
import money.epsilon.brokerage.platform.activity.ActivityService.ActivityView;
import money.epsilon.brokerage.platform.activity.ActivityService.Kind;
import money.epsilon.brokerage.platform.activity.ActivityService.Summary;
import money.epsilon.brokerage.platform.web.PageResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN, HO and FINANCE (see SecurityConfig): what happened, who did it, did it work. */
@RestController
@RequestMapping("/api/activity")
class ActivityController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ActivityService service;

    ActivityController(ActivityService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ActivityView> search(@RequestParam(required = false) Kind kind,
                                      @RequestParam(required = false) String by,
                                      @RequestParam(defaultValue = "false") boolean problemsOnly,
                                      @RequestParam(required = false) String reference,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return service.search(new ActivityFilter(kind, blankToNull(by), problemsOnly, blankToNull(reference), from, to), page, size);
    }

    @GetMapping("/summary")
    Summary summary() {
        return service.summary();
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
