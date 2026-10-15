package money.epsilon.brokerage.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

class OrgIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
    }

    long region(String name) throws Exception {
        return postJson("/api/regions", Map.of("name", name), admin, 201).get("id").asLong();
    }

    long branch(String name, long regionId, String from) throws Exception {
        return postJson("/api/branches", Map.of("name", name, "regionId", regionId, "from", from), admin, 201).get("id").asLong();
    }

    long employee(String type, String joinedOn) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("empCode", uniqueName("E"), "name", uniqueName("Person"), "type", type));
        if (joinedOn != null) body.put("joinedOn", joinedOn);
        return postJson("/api/employees", body, admin, 201).get("id").asLong();
    }

    void assign(long employeeId, long branchId, String from, int expected) throws Exception {
        postJson("/api/employees/" + employeeId + "/branch", Map.of("branchId", branchId, "from", from), admin, expected);
    }

    @Test
    void movingAnRmKeepsHistoryAndTheOrgChartAnswersForAnyDate() throws Exception {
        long east = region(uniqueName("EAST"));
        long kolkata = branch(uniqueName("KOLKATA"), east, "2026-04-01");
        long sasaram = branch(uniqueName("SASARAM"), east, "2026-04-01");
        long rm = employee("RM", "2025-01-01");

        assign(rm, kolkata, "2026-04-01", 204);
        assign(rm, sasaram, "2026-07-01", 204);

        JsonNode history = getJson("/api/employees/" + rm + "/branch", admin, 200);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("targetId").asLong()).isEqualTo(sasaram);
        assertThat(history.get(0).get("validTo").isNull()).isTrue();
        assertThat(history.get(1).get("targetId").asLong()).isEqualTo(kolkata);
        assertThat(history.get(1).get("validTo").asText()).isEqualTo("2026-06-30");

        assertThat(branchOf(rm, "2026-05-15", east)).isEqualTo(kolkata);
        assertThat(branchOf(rm, "2026-06-30", east)).isEqualTo(kolkata);
        assertThat(branchOf(rm, "2026-07-01", east)).isEqualTo(sasaram);
        assertThat(getJson("/api/employees/" + rm, admin, 200).get("branchId").asLong()).isEqualTo(sasaram);
    }

    @Test
    void historyCannotBeRewrittenAndDatesMustFitEmployment() throws Exception {
        long r = region(uniqueName("R"));
        long b1 = branch(uniqueName("B"), r, "2026-04-01");
        long b2 = branch(uniqueName("B"), r, "2026-04-01");
        long rm = employee("RM", "2026-03-01");

        assign(rm, b1, "2026-02-01", 409);   // before joining
        assign(rm, b1, "2026-04-01", 204);
        assign(rm, b2, "2026-03-15", 409);   // before the current assignment started
        assign(rm, b2, "2026-04-01", 409);   // same start day
        assign(rm, b1, "2026-05-01", 409);   // already there
        assertThat(getJson("/api/employees/" + rm + "/branch", admin, 200)).hasSize(1);
    }

    @Test
    void theDatabaseItselfRefusesOverlappingAssignments() throws Exception {
        long r = region(uniqueName("R"));
        long b = branch(uniqueName("B"), r, "2026-04-01");
        long rm = employee("RM", null);
        String insert = "INSERT INTO rm_branch_assignment (employee_id, branch_id, valid_from, valid_to, changed_by) VALUES (?, ?, ?::date, ?::date, 'test')";
        jdbc.sql(insert).params(rm, b, "2026-04-01", "2026-06-30").update();
        assertThatThrownBy(() -> jdbc.sql(insert).params(rm, b, "2026-06-30", null).update()).hasMessageContaining("exclusion");
        jdbc.sql(insert).params(rm, b, "2026-07-01", null).update();
    }

    @Test
    void rdRules() throws Exception {
        long north = region(uniqueName("NORTH"));
        long south = region(uniqueName("SOUTH"));
        long rd = employee("RM", null);
        long house = employee("HOUSE", null);

        postJson("/api/regions/" + north + "/rd", Map.of("employeeId", house, "from", "2026-04-01"), admin, 409);
        postJson("/api/regions/" + north + "/rd", Map.of("employeeId", rd, "from", "2026-04-01"), admin, 204);
        assertThat(rdOf(north)).isEqualTo(rd);

        // The same person becomes RD of another region: their old RD row closes, so 'north' has no RD from then on.
        postJson("/api/regions/" + south + "/rd", Map.of("employeeId", rd, "from", "2026-08-01"), admin, 204);
        assertThat(rdOf(south)).isEqualTo(rd);
        JsonNode northHistory = getJson("/api/regions/" + north + "/rd", admin, 200);
        assertThat(northHistory.get(0).get("validTo").asText()).isEqualTo("2026-07-31");
        JsonNode chart = getJson("/api/org?date=2026-07-31", admin, 200);
        assertThat(regionNode(chart, north).get("rd").get("id").asLong()).isEqualTo(rd);
    }

    @Test
    void exitClosesBranchAndRdOnTheLastDay() throws Exception {
        long r = region(uniqueName("R"));
        long b = branch(uniqueName("B"), r, "2026-04-01");
        long rm = employee("RM", "2026-01-01");
        assign(rm, b, "2026-04-01", 204);
        postJson("/api/regions/" + r + "/rd", Map.of("employeeId", rm, "from", "2026-04-01"), admin, 204);

        JsonNode exited = postJson("/api/employees/" + rm + "/exit", Map.of("lastDay", "2026-09-15", "reason", "Resigned"), admin, 200);
        assertThat(exited.get("status").asText()).isEqualTo("EXITED");
        assertThat(getJson("/api/employees/" + rm + "/branch", admin, 200).get(0).get("validTo").asText()).isEqualTo("2026-09-15");
        assertThat(getJson("/api/regions/" + r + "/rd", admin, 200).get(0).get("validTo").asText()).isEqualTo("2026-09-15");
        assertThat(branchOf(rm, "2026-09-15", r)).isEqualTo(b);
        assertThat(branchOf(rm, "2026-09-16", r)).isNull();

        postJson("/api/employees/" + rm + "/exit", Map.of("lastDay", "2026-09-20"), admin, 409);
        assign(rm, b, "2026-10-01", 409);
    }

    @Test
    void assignmentChangesAreAudited() throws Exception {
        long r = region(uniqueName("R"));
        long b1 = branch(uniqueName("B"), r, "2026-04-01");
        long b2 = branch(uniqueName("B"), r, "2026-04-01");
        long rm = employee("RM", null);
        assign(rm, b1, "2026-04-01", 204);
        assign(rm, b2, "2026-07-01", 204);

        JsonNode audit = getJson("/api/audit?entity=RM_BRANCH&size=50", admin, 200).get("items");
        var mine = StreamSupport.stream(audit.spliterator(), false)
                .filter(a -> {
                    JsonNode after = a.get("after");
                    JsonNode row = after.has("row") ? after.get("row") : after;
                    return row.get("subjectId").asLong() == rm;
                }).toList();
        assertThat(mine).extracting(a -> a.get("action").asText()).containsExactly("CREATE", "CLOSE", "CREATE");
        assertThat(mine.get(1).get("after").get("row").get("validTo").asText()).isEqualTo("2026-06-30");
        assertThat(mine.get(1).get("by").asText()).isEqualTo(ADMIN);
    }

    @Test
    void financeCanReadButNotChangeAndRmCannotSeeMasters() throws Exception {
        MockHttpSession finance = loginAs("FINANCE");
        getJson("/api/regions", finance, 200);
        getJson("/api/org", finance, 200);
        postJson("/api/regions", Map.of("name", uniqueName("X")), finance, 403);

        MockHttpSession rm = loginAs("RM");
        getJson("/api/employees", rm, 403);

        MockHttpSession ho = loginAs("HO");
        postJson("/api/regions", Map.of("name", uniqueName("HO-made")), ho, 201);
    }

    @Test
    void anRmCanBeAddedStraightIntoABranchWithAGeneratedCode() throws Exception {
        long r = region(uniqueName("R"));
        long b = branch(uniqueName("B"), r, "2026-08-01");
        JsonNode rm = postJson("/api/employees", Map.of("name", "NEW RM", "type", "RM", "branchId", b, "from", "2026-08-01"), admin, 201);
        assertThat(rm.get("empCode").asText()).startsWith("RM-");
        assertThat(rm.get("branchId").asLong()).isEqualTo(b);

        // a bad branch leaves nothing behind
        String name = uniqueName("Ghost");
        postJson("/api/employees", Map.of("name", name, "type", "RM", "branchId", 999999, "from", "2026-08-01"), admin, 404);
        assertThat(getJson("/api/employees", admin, 200).toString()).doesNotContain(name);
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() throws Exception {
        String name = uniqueName("Dup");
        region(name);
        postJson("/api/regions", Map.of("name", name.toUpperCase()), admin, 409);
    }

    private Long branchOf(long employeeId, String date, long regionId) throws Exception {
        JsonNode chart = getJson("/api/org?date=" + date, admin, 200);
        for (JsonNode branch : regionNode(chart, regionId).get("branches")) {
            for (JsonNode person : branch.get("people")) {
                if (person.get("id").asLong() == employeeId) return branch.get("id").asLong();
            }
        }
        return null;
    }

    private long rdOf(long regionId) throws Exception {
        return getJson("/api/regions", admin, 200).findParents("id").stream()
                .filter(n -> n.get("id").asLong() == regionId && n.has("currentRd"))
                .findFirst().orElseThrow().get("currentRd").get("id").asLong();
    }

    private JsonNode regionNode(JsonNode chart, long regionId) {
        return StreamSupport.stream(chart.get("regions").spliterator(), false)
                .filter(r -> r.get("id").asLong() == regionId).findFirst().orElseThrow();
    }
}
