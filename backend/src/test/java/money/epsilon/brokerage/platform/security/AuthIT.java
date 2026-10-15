package money.epsilon.brokerage.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthIT extends IntegrationTestBase {

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void csrfEndpointSetsTokenCookie() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void apiNeedsLogin() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", ADMIN, "password", ADMIN_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRejectedWithoutSayingWhy() throws Exception {
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", ADMIN, "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", "nobody-here", "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void bootstrapAdminCanLogInAndSeeThemselves() throws Exception {
        MockHttpSession session = loginAdmin();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void usernameIsCaseInsensitiveAtLogin() throws Exception {
        String name = uniqueName("case");
        createUser(name, "RM");
        login(name.toUpperCase(), PASSWORD);
    }

    @Test
    void nonAdminCannotReachAdminEndpoints() throws Exception {
        String name = uniqueName("rm");
        createUser(name, "RM");
        MockHttpSession session = login(name, PASSWORD);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String name = uniqueName("leaver");
        long id = createUser(name, "HO");
        MockHttpSession session = login(name, PASSWORD);

        mvc.perform(withJson(patch("/api/users/" + id), Map.of("active", false)).session(loginAdmin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(withJson(post("/api/auth/login"), Map.of("username", name, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangeAppliesToExistingSession() throws Exception {
        String name = uniqueName("promoted");
        long id = createUser(name, "RM");
        MockHttpSession session = login(name, PASSWORD);
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());

        mvc.perform(withJson(patch("/api/users/" + id), Map.of("role", "ADMIN")).session(loginAdmin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = loginAdmin();
        mvc.perform(withCsrf(post("/api/auth/logout")).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
