package money.epsilon.brokerage.ingestion.clientload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.Decisions;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.LoadResult;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies a previewed client load in one transaction (all or nothing). */
@Component
class ClientLoadJob implements JobHandler {

    static final String TYPE = "client-load";

    private final ClientLoadService service;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final ObjectMapper mapper;

    ClientLoadJob(ClientLoadService service, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        this.service = service;
        this.tx = tx;
        this.audit = audit;
        this.mapper = mapper;
    }

    record Payload(String token, String fileName, Decisions decisions) {
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public JsonNode run(JsonNode payloadJson) throws Exception {
        Payload p = mapper.treeToValue(payloadJson, Payload.class);
        LoadResult result = tx.execute(status -> {
            LoadResult r = service.apply(p.token(), p.fileName(), p.decisions());
            audit.record("CLIENT_LOAD", null, "LOAD", null, r);
            return r;
        });
        return mapper.valueToTree(result);
    }
}
