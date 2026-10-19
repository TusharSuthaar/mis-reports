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
    public static final String PROGRESS = "test.progress";
    public static final String SLOW_IMPORT = "test.slow-import";
    /** Holds {@link #SLOW_IMPORT} running until a test counts it down. */
    public static volatile java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(0);

    @Bean
    JobHandler echoJob() {
        return new JobHandler() {
            @Override public String type() { return ECHO; }
            @Override public String title() { return "Test echo"; }
            @Override public JsonNode run(JsonNode payload) { return payload; }
        };
    }

    /** Reports two steps from inside its own transaction, as real imports do. */
    @Bean
    JobHandler progressJob(org.springframework.transaction.support.TransactionTemplate tx) {
        return new JobHandler() {
            @Override public String type() { return PROGRESS; }
            @Override public String title() { return "Test progress"; }
            @Override public JsonNode run(JsonNode payload) {
                tx.executeWithoutResult(s -> {
                    var p = money.epsilon.brokerage.platform.job.JobProgress.current();
                    p.steps(2);
                    p.step("Reading", 3);
                    for (int i = 0; i < 3; i++) p.advance();
                    p.step("Saving", 2);
                    p.advance();
                    p.advance();
                });
                return null;
            }
        };
    }

    /** Like an import: one at a time; waits for {@link #release}. */
    @Bean
    JobHandler slowImportJob() {
        return new JobHandler() {
            @Override public String type() { return SLOW_IMPORT; }
            @Override public String title() { return "Test import"; }
            @Override public boolean oneAtATime() { return true; }
            @Override public JsonNode run(JsonNode payload) throws Exception {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return null;
            }
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
