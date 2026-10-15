package money.epsilon.brokerage.sharing.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.importing.TransactionalJob;
import money.epsilon.brokerage.sharing.load.ShareLoadService.LoadResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies a previewed Master MIS upload in one transaction (all or nothing). */
@Component
class ShareLoadJob extends TransactionalJob<ShareLoadJob.Payload> {

    static final String TYPE = "share-load";

    private final ShareLoadService service;
    private final AuditService audit;

    ShareLoadJob(ShareLoadService service, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        super(TYPE, Payload.class, tx, mapper);
        this.service = service;
        this.audit = audit;
    }

    record Payload(String token, String fileName, LocalDate from) {
    }

    @Override
    protected Object apply(Payload p, TransactionStatus status) {
        LoadResult r = service.apply(p.token(), p.fileName(), p.from());
        audit.record("SHARE_LOAD", null, "LOAD", null, r);
        return r;
    }
}
