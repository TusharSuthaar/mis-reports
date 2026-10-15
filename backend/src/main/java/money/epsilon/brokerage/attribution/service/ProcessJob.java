package money.epsilon.brokerage.attribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.platform.importing.TransactionalJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: splits a month, all or nothing (AttributionService.process writes the audit record). */
@Component
class ProcessJob extends TransactionalJob<ProcessJob.Payload> {

    static final String TYPE = "process-month";

    private final AttributionService service;

    ProcessJob(AttributionService service, TransactionTemplate tx, ObjectMapper mapper) {
        super(TYPE, Payload.class, tx, mapper);
        this.service = service;
    }

    record Payload(long periodId) {
    }

    @Override
    protected Object apply(Payload p, TransactionStatus status) {
        return service.process(p.periodId());
    }
}
