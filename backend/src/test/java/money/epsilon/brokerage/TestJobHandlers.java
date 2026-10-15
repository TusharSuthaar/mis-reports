package money.epsilon.brokerage;

import com.fasterxml.jackson.databind.JsonNode;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Job types that exist only in tests, to exercise the job runner. */
@TestConfiguration
public class TestJobHandlers {

    public static final String ECHO = "test.echo";
    public static final String FAIL = "test.fail";

    @Bean
    JobHandler echoJob() {
        return new JobHandler() {
            @Override public String type() { return ECHO; }
            @Override public JsonNode run(JsonNode payload) { return payload; }
        };
    }

    @Bean
    JobHandler failingJob() {
        return new JobHandler() {
            @Override public String type() { return FAIL; }
            @Override public JsonNode run(JsonNode payload) { throw new IllegalStateException("boom"); }
        };
    }
}
