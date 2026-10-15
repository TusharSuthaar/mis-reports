package money.epsilon.brokerage.ingestion.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.platform.importing.TransactionalJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: reads an uploaded brokerage file and stages it (all or nothing). */
@Component
class UploadJob extends TransactionalJob<UploadJob.Payload> {

    static final String TYPE = "income-upload";

    private final UploadService service;

    UploadJob(UploadService service, TransactionTemplate tx, ObjectMapper mapper) {
        super(TYPE, "Uploaded a brokerage file", Payload.class, tx, mapper);
        this.service = service;
    }

    record Payload(String path, String fileName, String sha256, long periodId) {
    }

    @Override
    protected Object apply(Payload p, TransactionStatus status) {
        return service.stage(p);
    }
}
