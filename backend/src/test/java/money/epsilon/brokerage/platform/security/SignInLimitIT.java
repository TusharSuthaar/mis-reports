package money.epsilon.brokerage.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * R-101: the sign-in limit. Each test signs in from its own address, so its failures never pause the other tests'
 * sign-ins (they all come from 127.0.0.1).
 */
class SignInLimitIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    private MvcResult attempt(String username, String password, String address) throws Exception {
        return mvc.perform(withJson(post("/api/auth/login"), Map.of("username", username, "password", password))
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                })).andReturn();
    }

    private List<String> audited(long userId) {
        return jdbc.sql("SELECT action FROM audit_log WHERE entity = 'USER' AND entity_id = ? ORDER BY id").param(String.valueOf(userId))
                .query(String.class).list();
    }

    @Test
    void wrongPasswordsMakeTheUserWaitThenLockUntilAnAdministratorUnlocks() throws Exception {
        String name = uniqueName("limit");
        long id = createUser(name, "HO");
        String address = "10.77.0.1";

        for (int i = 1; i <= 5; i++) assertThat(attempt(name, "wrong-password-" + i, address).getResponse().getStatus()).isEqualTo(401);
        // Five wrong in a row: a wait, and the right password is not even looked at until it is over.
        MvcResult waiting = attempt(name, PASSWORD, address);
        assertThat(waiting.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(waiting).get("detail").asText()).isEqualTo("Too many wrong passwords. Try again in 1 minute.");

        // Each wrong password after a wait makes the next wait longer; the tenth locks the account.
        for (int i = 6; i <= 10; i++) {
            jdbc.sql("UPDATE app_user SET locked_until = now() - interval '1 second' WHERE id = ?").param(id).update();
            assertThat(attempt(name, "wrong-password-" + i, address).getResponse().getStatus()).isEqualTo(401);
        }
        jdbc.sql("UPDATE app_user SET locked_until = NULL WHERE id = ?").param(id).update();
        MvcResult locked = attempt(name, PASSWORD, address);
        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(locked).get("detail").asText()).contains("locked").contains("administrator");
        assertThat(audited(id)).contains("SIGN_IN_FAILED", "SIGN_IN_LOCKED").doesNotContain("SIGN_IN");

        // The administrator sees it on the Users screen and unlocks it; the right password works again, and is audited.
        MockHttpSession admin = loginAdmin();
        JsonNode user = java.util.stream.StreamSupport.stream(getJson("/api/users", admin, 200).spliterator(), false)
                .filter(u -> u.get("id").asLong() == id).findFirst().orElseThrow();
        assertThat(user.get("locked").asBoolean()).isTrue();
        assertThat(postJson("/api/users/" + id + "/unlock", Map.of(), admin, 200).get("locked").asBoolean()).isFalse();
        assertThat(attempt(name, PASSWORD, address).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT failed_logins FROM app_user WHERE id = ?").param(id).query(Integer.class).single()).isZero();
        assertThat(audited(id)).contains("UNLOCK", "SIGN_IN");
    }

    @Test
    void manyFailuresFromOneAddressPauseItWhateverTheUsernames() throws Exception {
        String name = uniqueName("addr");
        createUser(name, "HO");
        String busy = "10.77.0.2", other = "10.77.0.3";
        for (int i = 0; i < 20; i++) attempt(uniqueName("nobody"), "guess-" + i, busy);
        MvcResult paused = attempt(name, PASSWORD, busy);
        assertThat(paused.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(paused).get("detail").asText()).contains("from this computer");
        assertThat(attempt(name, PASSWORD, other).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void signingOutIsAudited() throws Exception {
        String name = uniqueName("out");
        long id = createUser(name, "HO");
        MvcResult in = attempt(name, PASSWORD, "10.77.0.4");
        mvc.perform(withCsrf(post("/api/auth/logout")).session((MockHttpSession) in.getRequest().getSession(false)));
        assertThat(audited(id)).contains("SIGN_IN", "SIGN_OUT");
    }
}
