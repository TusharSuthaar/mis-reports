package money.epsilon.brokerage.sharing.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.job.JobHandler;
import money.epsilon.brokerage.sharing.load.ShareLoadService.LoadResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies a previewed partner-share load in one transaction (all or nothing). */
@Component
class ShareLoadJob implements JobHandler {

    static final String TYPE = "share-load";

    private final ShareLoadService service;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final ObjectMapper mapper;

    ShareLoadJob(ShareLoadService service, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        this.service = service;
        this.tx = tx;
        this.audit = audit;
        this.mapper = mapper;
    }

    record Payload(String token, String fileName, LocalDate from) {
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public JsonNode run(JsonNode payloadJson) throws Exception {
        Payload p = mapper.treeToValue(payloadJson, Payload.class);
        LoadResult result = tx.execute(status -> {
            LoadResult r = service.apply(p.token(), p.fileName(), p.from());
            audit.record("SHARE_LOAD", null, "LOAD", null, r);
            return r;
        });
        return mapper.valueToTree(result);
    }
}
