package money.epsilon.brokerage.platform.audit;

import java.time.Instant;
import money.epsilon.brokerage.platform.audit.AuditService.AuditFilter;
import money.epsilon.brokerage.platform.audit.AuditService.AuditView;
import money.epsilon.brokerage.platform.web.PageResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/audit")
class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AuditView> search(@RequestParam(required = false) String entity,
                                   @RequestParam(required = false) String entityId,
                                   @RequestParam(required = false) String action,
                                   @RequestParam(required = false) String by,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        AuditFilter filter = new AuditFilter(blankToNull(entity), blankToNull(entityId), blankToNull(action),
                blankToNull(by), from, to);
        return service.search(filter, page, size);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
