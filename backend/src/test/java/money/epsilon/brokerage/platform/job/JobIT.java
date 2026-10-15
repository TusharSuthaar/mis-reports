package money.epsilon.brokerage.platform.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.TestJobHandlers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

class JobIT extends IntegrationTestBase {

    @Autowired
    JobService jobs;

    @Autowired
    JobRepository repository;

    @Autowired
    UserDetailsService userDetailsService;

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulJobEndsDoneWithResult() throws Exception {
        long id = jobs.submit(TestJobHandlers.ECHO, json.readTree("{\"rows\": 3}"));

        awaitFinished(id);
        Job job = repository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(json.readTree(job.getResult()).get("rows").asInt()).isEqualTo(3);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isAfterOrEqualTo(job.getStartedAt());
    }

    @Test
    void failingJobEndsFailedWithError() {
        long id = jobs.submit(TestJobHandlers.FAIL, null);

        awaitFinished(id);
        Job job = repository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getError()).isEqualTo("boom");
    }

    @Test
    void unknownJobTypeIsRejected() {
        assertThatThrownBy(() -> jobs.submit("no.such.job", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyTheSubmitterAndAdminCanSeeAJob() throws Exception {
        String owner = uniqueName("owner");
        String other = uniqueName("other");
        createUser(owner, "HO");
        createUser(other, "HO");

        actAs(owner);
        long id = jobs.submit(TestJobHandlers.ECHO, null);
        clearUser();
        awaitFinished(id);

        mvc.perform(get("/api/jobs/" + id).session(login(owner, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.createdBy").value(owner));
        mvc.perform(get("/api/jobs/" + id).session(loginAdmin())).andExpect(status().isOk());
        MockHttpSession otherSession = login(other, PASSWORD);
        mvc.perform(get("/api/jobs/" + id).session(otherSession)).andExpect(status().isNotFound());
    }

    private void actAs(String username) {
        UserDetails user = userDetailsService.loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    private void awaitFinished(long id) {
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            JobStatus s = repository.findById(id).orElseThrow().getStatus();
            return s == JobStatus.DONE || s == JobStatus.FAILED;
        });
    }
}
