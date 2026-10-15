package money.epsilon.brokerage.ingestion.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.ingestion.upload.UploadService.StageResult;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: reads an uploaded income file and stages it (all or nothing). */
@Component
class UploadJob implements JobHandler {

    static final String TYPE = "income-upload";

    private final UploadService service;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    UploadJob(UploadService service, TransactionTemplate tx, ObjectMapper mapper) {
        this.service = service;
        this.tx = tx;
        this.mapper = mapper;
    }

    record Payload(String path, String fileName, String sha256, long periodId) {
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public JsonNode run(JsonNode payloadJson) throws Exception {
        Payload p = mapper.treeToValue(payloadJson, Payload.class);
        StageResult result = tx.execute(status -> service.stage(p));
        return mapper.valueToTree(result);
    }
}
