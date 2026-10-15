package money.epsilon.brokerage.platform.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.TestJobHandlers;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.logging.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Phase C: every request and job has a reference; errors show only the reference; the Activity screen lists it all. */
class ActivityIT extends IntegrationTestBase {

    @Autowired
    JobService jobs;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void everyResponseCarriesAReferenceAndASaneIncomingOneIsKept() throws Exception {
        String generated = mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestContext.HEADER);
        assertThat(generated).matches("[2-9A-HJKMNP-Z]{8}");

        mvc.perform(get("/api/health").header(RequestContext.HEADER, "PROXY-REF-123"))
                .andExpect(header().string(RequestContext.HEADER, "PROXY-REF-123"));
        String replaced = mvc.perform(get("/api/health").header(RequestContext.HEADER, "bad id <script>"))
                .andReturn().getResponse().getHeader(RequestContext.HEADER);
        assertThat(replaced).isNotEqualTo("bad id <script>").hasSize(8);
    }

    @Test
    void errorAnswersCarryTheSameReferenceAsTheHeader() throws Exception {
        MockHttpSession admin = loginAdmin();
        MvcResult notFound = mvc.perform(get("/api/jobs/999999999").session(admin)).andExpect(status().isNotFound()).andReturn();
        assertThat(body(notFound).get("reference").asText()).isEqualTo(notFound.getResponse().getHeader(RequestContext.HEADER));

        // Spring's own errors (unreadable JSON here) keep their status and get a reference too.
        MvcResult badJson = mvc.perform(withCsrf(post("/api/users")).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badJson).get("reference").asText()).isEqualTo(badJson.getResponse().getHeader(RequestContext.HEADER));
    }

    @Test
    void anUnexpectedErrorShowsOnlyTheReferenceAndIsKeptMasked() throws Exception {
        MockHttpSession admin = loginAdmin();
        MvcResult result = mvc.perform(get(TestJobHandlers.BOOM_PATH).session(admin))
                .andExpect(status().isInternalServerError()).andReturn();
        String reference = result.getResponse().getHeader(RequestContext.HEADER);
        JsonNode problem = body(result);
        assertThat(problem.get("reference").asText()).isEqualTo(reference);
        assertThat(problem.get("detail").asText()).contains(reference).doesNotContain("broke").doesNotContain(TestJobHandlers.BOOM_PAN);

        String kept = jdbc.sql("SELECT message FROM error_log WHERE reference = :r").param("r", reference).query(String.class).single();
        assertThat(kept).contains("broke while reading").contains("ZZZZZ****Z").doesNotContain(TestJobHandlers.BOOM_PAN);

        JsonNode found = getJson("/api/activity?reference=" + reference, admin, 200);
        assertThat(found.get("total").asInt()).isEqualTo(1);
        JsonNode row = found.get("items").get(0);
        assertThat(row.get("kind").asText()).isEqualTo("ERROR");
        assertThat(row.get("result").asText()).isEqualTo("FAILED");
        assertThat(row.get("by").asText()).isEqualTo(ADMIN);
        assertThat(row.get("title").asText()).isEqualTo("Unexpected error on GET " + TestJobHandlers.BOOM_PATH);
    }

    @Test
    void aJobKeepsTheReferenceOfTheRequestThatStartedItInTheRecordAndItsLogLines() throws Exception {
        Logger runnerLog = (Logger) LoggerFactory.getLogger("money.epsilon.brokerage.platform.job.JobRunner");
        ListAppender<ILoggingEvent> lines = new ListAppender<>();
        lines.start();
        runnerLog.addAppender(lines);
        try {
            MDC.put(RequestContext.REQUEST_ID, "TESTREF7");
            long ok = jobs.submit(TestJobHandlers.ECHO, null);
            long failed = jobs.submit(TestJobHandlers.FAIL, null);
            MDC.clear();
            awaitFinished(ok);
            awaitFinished(failed);

            MockHttpSession admin = loginAdmin();
            JsonNode job = getJson("/api/jobs/" + failed, admin, 200);
            assertThat(job.get("reference").asText()).isEqualTo("TESTREF7");
            assertThat(job.get("title").asText()).isEqualTo("Test failure");

            JsonNode found = getJson("/api/activity?kind=JOB&reference=testref7", admin, 200);
            assertThat(found.get("total").asInt()).isEqualTo(2);
            JsonNode problems = getJson("/api/activity?kind=JOB&problemsOnly=true&reference=TESTREF7", admin, 200);
            assertThat(problems.get("total").asInt()).isEqualTo(1);
            assertThat(problems.get("items").get(0).get("detail").asText()).isEqualTo("boom");

            assertThat(lines.list).filteredOn(e -> e.getFormattedMessage().equals("Job " + failed + " failed")).singleElement()
                    .satisfies(e -> {
                        assertThat(e.getMDCPropertyMap()).containsEntry(RequestContext.REQUEST_ID, "TESTREF7")
                                .containsEntry(RequestContext.JOB_ID, String.valueOf(failed))
                                .containsEntry(RequestContext.ACTION, TestJobHandlers.FAIL);
                        assertThat(e.getThrowableProxy()).isNotNull();
                    });
        } finally {
            runnerLog.detachAppender(lines);
        }
    }

    @Test
    void operationalEventsFromTheAuditLogShowInPlainWords() throws Exception {
        MockHttpSession admin = loginAdmin();
        long id = createUser(uniqueName("reset"), "HO");
        postJson("/api/users/" + id + "/password", Map.of("password", "Another-Horse-42"), admin, 204);

        JsonNode found = getJson("/api/activity?kind=EVENT&by=" + ADMIN + "&size=200", admin, 200);
        assertThat(found.get("items").findValuesAsText("title")).contains("Reset a user's password");
        // Everyday master edits (creating the user) stay on the Audit log screen.
        assertThat(found.get("items").findValuesAsText("title")).noneMatch(t -> t.startsWith("USER:"));
    }

    @Test
    void activityIsForManagersOnly() throws Exception {
        mvc.perform(get("/api/activity").session(loginAs("RM"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/activity/summary").session(loginAs("FINANCE"))).andExpect(status().isOk());
    }

    private void awaitFinished(long id) {
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            String s = jdbc.sql("SELECT status FROM job WHERE id = :id").param("id", id).query(String.class).single();
            return s.equals("DONE") || s.equals("FAILED");
        });
    }
}
