package money.epsilon.brokerage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import money.epsilon.brokerage.client.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the whole app against a real Postgres (one container for the test run). Tests share the database,
 * so each test creates its own uniquely named users.
 */
@SpringBootTest(properties = {
        "mis.bootstrap-admin.username=" + IntegrationTestBase.ADMIN,
        "mis.bootstrap-admin.password=" + IntegrationTestBase.ADMIN_PASSWORD,
        "mis.pan.key=" + IntegrationTestBase.TEST_KEY,
        "mis.pan.hash-key=" + IntegrationTestBase.TEST_KEY,
        "mis.storage.dir=target/it-files",
        // MockMvc sessions are passed directly; database-backed sessions are covered by SessionPersistenceIT
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"})
@AutoConfigureMockMvc
@Import(TestJobHandlers.class)
public abstract class IntegrationTestBase {

    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASSWORD = "Bootstrap-Pass-123";
    protected static final String PASSWORD = "Correct-Horse-9";
    /** 32 zero bytes, base64. Test-only key. */
    static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final AtomicInteger SEQ = new AtomicInteger();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected ClientService clientService;

    @Autowired
    private UserDetailsService userDetails;

    /** A Master MIS client: the PAN's client, with this RM from {@code from}. Runs as the admin. */
    protected long masterClient(String name, String pan, long rmId, String from) {
        var principal = userDetails.loadUserByUsername(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        try {
            long id = clientService.createUncoded(name, pan);
            clientService.changeRm(id, rmId, LocalDate.parse(from), "test: Master MIS client");
            return id;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Uploads a Finesse client master export. */
    protected JsonNode uploadClientMaster(byte[] file, MockHttpSession session) throws Exception {
        return body(mvc.perform(withCsrf(multipart("/api/finesse/client-master").file(new MockMultipartFile("file", "ClientMasterReport.xlsx", null, file)))
                .session(session)).andExpect(status().isOk()).andReturn());
    }

    protected static String uniqueName(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000;
    }

    protected MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(withJson(post("/api/auth/login"), Map.of("username", username, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession loginAdmin() throws Exception {
        return login(ADMIN, ADMIN_PASSWORD);
    }

    /** Creates a user through the API as admin and returns its id. */
    protected long createUser(String username, String role) throws Exception {
        MvcResult result = mvc.perform(withJson(post("/api/users"),
                        Map.of("username", username, "displayName", "Test " + username, "role", role, "password", PASSWORD))
                        .session(loginAdmin()))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return withCsrf(request)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    /**
     * What the SPA does: take the XSRF-TOKEN cookie and echo it in the X-XSRF-TOKEN header. (spring-security-test's
     * csrf() is not used: it swaps the app's cookie token store for a session one for the rest of the run.)
     */
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    protected JsonNode getJson(String path, MockHttpSession session, int expectedStatus) throws Exception {
        return body(mvc.perform(get(path).session(session)).andExpect(status().is(expectedStatus)).andReturn());
    }

    protected JsonNode postJson(String path, Object body, MockHttpSession session, int expectedStatus) throws Exception {
        return body(mvc.perform(withJson(post(path), body).session(session)).andExpect(status().is(expectedStatus)).andReturn());
    }

    protected JsonNode patchJson(String path, Object body, MockHttpSession session, int expectedStatus) throws Exception {
        return body(mvc.perform(withJson(patch(path), body).session(session)).andExpect(status().is(expectedStatus)).andReturn());
    }

    protected JsonNode putJson(String path, Object body, MockHttpSession session, int expectedStatus) throws Exception {
        return body(mvc.perform(withJson(put(path), body).session(session)).andExpect(status().is(expectedStatus)).andReturn());
    }

    /** Logs in a fresh user with the given role. */
    protected MockHttpSession loginAs(String role) throws Exception {
        String name = uniqueName(role.toLowerCase());
        createUser(name, role);
        return login(name, PASSWORD);
    }
}
