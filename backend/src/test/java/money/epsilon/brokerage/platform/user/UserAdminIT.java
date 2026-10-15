package money.epsilon.brokerage.platform.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class UserAdminIT extends IntegrationTestBase {

    @Test
    void creatingAUserIsAuditedWithoutThePassword() throws Exception {
        String name = uniqueName("new");
        long id = createUser(name, "FINANCE");

        JsonNode entry = latestAudit(id);
        assertThat(entry.get("action").asText()).isEqualTo("CREATE");
        assertThat(entry.get("by").asText()).isEqualTo(ADMIN);
        assertThat(entry.get("before").isNull()).isTrue();
        assertThat(entry.get("after").get("username").asText()).isEqualTo(name);
        assertThat(entry.get("after").get("role").asText()).isEqualTo("FINANCE");
        assertThat(entry.toString()).doesNotContain(PASSWORD).doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    void roleChangeIsAuditedWithBeforeAndAfter() throws Exception {
        long id = createUser(uniqueName("mover"), "RM");
        mvc.perform(withJson(patch("/api/users/" + id), Map.of("role", "RD")).session(loginAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RD"));

        JsonNode entry = latestAudit(id);
        assertThat(entry.get("action").asText()).isEqualTo("UPDATE");
        assertThat(entry.get("before").get("role").asText()).isEqualTo("RM");
        assertThat(entry.get("after").get("role").asText()).isEqualTo("RD");
    }

    @Test
    void duplicateUsernameIsRejectedIgnoringCase() throws Exception {
        String name = uniqueName("twin");
        createUser(name, "RM");
        mvc.perform(withJson(post("/api/users"),
                        Map.of("username", name.toUpperCase(), "displayName", "Twin", "role", "RM", "password", PASSWORD))
                        .session(loginAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndBadUsernameAreRejected() throws Exception {
        MockHttpSession admin = loginAdmin();
        mvc.perform(withJson(post("/api/users"),
                        Map.of("username", uniqueName("weak"), "displayName", "W", "role", "RM", "password", "short"))
                        .session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/users"),
                        Map.of("username", "has space", "displayName", "W", "role", "RM", "password", PASSWORD))
                        .session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/users"),
                        Map.of("username", uniqueName("norole"), "displayName", "W", "password", PASSWORD))
                        .session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotChangeOwnAccess() throws Exception {
        MockHttpSession admin = loginAdmin();
        long adminId = body(mvc.perform(get("/api/auth/me").session(admin)).andReturn()).get("id").asLong();

        mvc.perform(withJson(patch("/api/users/" + adminId), Map.of("active", false)).session(admin))
                .andExpect(status().isConflict());
        mvc.perform(withJson(patch("/api/users/" + adminId), Map.of("role", "RM")).session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void passwordResetLetsUserLogInWithNewPasswordAndIsAudited() throws Exception {
        String name = uniqueName("forgetful");
        long id = createUser(name, "RM");
        String newPassword = "Brand-New-Pass-42";

        mvc.perform(withJson(post("/api/users/" + id + "/password"), Map.of("password", newPassword)).session(loginAdmin()))
                .andExpect(status().isNoContent());

        login(name, newPassword);
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", name, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(latestAudit(id).get("action").asText()).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(withJson(patch("/api/users/999999"), Map.of("displayName", "x")).session(loginAdmin()))
                .andExpect(status().isNotFound());
    }

    private JsonNode latestAudit(long userId) throws Exception {
        JsonNode page = body(mvc.perform(get("/api/audit").param("entity", "USER").param("entityId", String.valueOf(userId))
                        .session(loginAdmin()))
                .andExpect(status().isOk())
                .andReturn());
        return page.get("items").get(0);
    }
}
