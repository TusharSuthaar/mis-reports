package money.epsilon.brokerage.platform.job;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Background work of one type. Implement as a Spring bean; {@link JobService#submit} routes jobs by {@link #type()}.
 * Throwing marks the job FAILED with the exception message.
 */
public interface JobHandler {

    String type();

    /** @return the job result, stored as JSON (may be null) */
    JsonNode run(JsonNode payload) throws Exception;
}
