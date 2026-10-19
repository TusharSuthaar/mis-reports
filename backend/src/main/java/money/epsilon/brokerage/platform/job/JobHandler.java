package money.epsilon.brokerage.platform.job;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Background work of one type. Implement as a Spring bean; {@link JobService#submit} routes jobs by {@link #type()}.
 * Throwing marks the job FAILED with the exception message.
 */
public interface JobHandler {

    String type();

    /** What the job does, in plain words, for the Activity screen (e.g. "Uploaded a brokerage file"). */
    String title();

    /** @return the job result, stored as JSON (may be null) */
    JsonNode run(JsonNode payload) throws Exception;

    /**
     * True for work that changes data from a plan made at its start (imports, loads, processing): two at once would both
     * plan from the same data (e.g. both add the same clients). A second one is refused while one is queued or running.
     */
    default boolean oneAtATime() {
        return false;
    }
}
