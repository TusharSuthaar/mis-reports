package money.epsilon.brokerage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Logins live in the database (Spring Session JDBC), so a restart does not sign anyone out. */
@SpringBootTest(properties = {
        "mis.bootstrap-admin.username=" + IntegrationTestBase.ADMIN,
        "mis.bootstrap-admin.password=" + IntegrationTestBase.ADMIN_PASSWORD,
        "mis.pan.key=" + IntegrationTestBase.TEST_KEY,
        "mis.pan.hash-key=" + IntegrationTestBase.TEST_KEY,
        "mis.storage.dir=target/it-files"})
class SessionPersistenceIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    @Test
    void aLoginIsStoredInTheDatabaseAndRecognisedByItsCookie() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var login = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie session = login.getCookie("SESSION");
        assertThat(session).as("session cookie").isNotNull();

        long stored = jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = ?").param(ADMIN).query(Long.class).single();
        assertThat(stored).isPositive();

        // A fresh request carrying only the cookie (as after a server restart) is still signed in.
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(ADMIN));
    }
}
