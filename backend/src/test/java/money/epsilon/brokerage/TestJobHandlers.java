package money.epsilon.brokerage;

import com.fasterxml.jackson.databind.JsonNode;
import money.epsilon.brokerage.platform.job.JobHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Job types and an endpoint that exist only in tests, to exercise the job runner and error handling. */
@TestConfiguration
public class TestJobHandlers {

    public static final String ECHO = "test.echo";
    public static final String FAIL = "test.fail";

    @Bean
    JobHandler echoJob() {
        return new JobHandler() {
            @Override public String type() { return ECHO; }
            @Override public String title() { return "Test echo"; }
            @Override public JsonNode run(JsonNode payload) { return payload; }
        };
    }

    @Bean
    JobHandler failingJob() {
        return new JobHandler() {
            @Override public String type() { return FAIL; }
            @Override public String title() { return "Test failure"; }
            @Override public JsonNode run(JsonNode payload) { throw new IllegalStateException("boom"); }
        };
    }

    public static final String BOOM_PATH = "/api/test/boom";
    /** Made-up PAN in the error message: it must come out masked everywhere. */
    public static final String BOOM_PAN = "ZZZZZ9999Z";

    /** Fails the way a bug would: an unexpected exception. (Found by component scanning: test classpath only.) */
    @RestController
    public static class BoomController {
        @GetMapping(BOOM_PATH)
        String boom() {
            throw new IllegalStateException("broke while reading " + BOOM_PAN);
        }
    }
}
