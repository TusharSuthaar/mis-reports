package money.epsilon.brokerage.attribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import money.epsilon.brokerage.period.service.PeriodService;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starting the split of a month as a background job. */
@Service
public class MonthActions {

    private final JobService jobs;
    private final PeriodService periods;
    private final ObjectMapper mapper;

    MonthActions(JobService jobs, PeriodService periods, ObjectMapper mapper) {
        this.jobs = jobs;
        this.periods = periods;
        this.mapper = mapper;
    }

    @Transactional
    public long startProcessing(long periodId) {
        if (periods.get(periodId).status() == money.epsilon.brokerage.period.domain.Period.Status.LOCKED) {
            throw new RuleViolationException("A locked month cannot be processed again");
        }
        return jobs.submit(ProcessJob.TYPE, mapper.valueToTree(Map.of("periodId", periodId)));
    }
}
