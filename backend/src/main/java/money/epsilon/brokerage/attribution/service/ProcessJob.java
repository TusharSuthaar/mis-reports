package money.epsilon.brokerage.attribution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.stereotype.Component;

/** Background job: splits a month (AttributionService.process runs in its own transaction). */
@Component
class ProcessJob implements JobHandler {

    static final String TYPE = "process-month";

    private final AttributionService service;
    private final ObjectMapper mapper;

    ProcessJob(AttributionService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    record Payload(long periodId) {
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public JsonNode run(JsonNode payloadJson) throws Exception {
        Payload p = mapper.treeToValue(payloadJson, Payload.class);
        return mapper.valueToTree(service.process(p.periodId()));
    }
}
