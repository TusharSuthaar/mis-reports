package money.epsilon.brokerage.ingestion.clientload;

import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.Decisions;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.LoadResult;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.importing.TransactionalJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies a previewed client load in one transaction (all or nothing). */
@Component
class ClientLoadJob extends TransactionalJob<ClientLoadJob.Payload> {

    static final String TYPE = "client-load";

    private final ClientLoadService service;
    private final AuditService audit;

    ClientLoadJob(ClientLoadService service, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        super(TYPE, Payload.class, tx, mapper);
        this.service = service;
        this.audit = audit;
    }

    record Payload(String token, String fileName, Decisions decisions) {
    }

    @Override
    protected Object apply(Payload p, TransactionStatus status) {
        LoadResult r = service.apply(p.token(), p.fileName(), p.decisions());
        audit.record("CLIENT_LOAD", null, "LOAD", null, r);
        return r;
    }
}
