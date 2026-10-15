package money.epsilon.brokerage.platform.job;

import money.epsilon.brokerage.platform.job.JobService.JobView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Jobs are submitted by the feature that owns them (e.g. a bulk import); this only reports progress. */
@RestController
@RequestMapping("/api/jobs")
class JobController {

    private final JobService service;

    JobController(JobService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    JobView get(@PathVariable long id) {
        return service.get(id);
    }
}
