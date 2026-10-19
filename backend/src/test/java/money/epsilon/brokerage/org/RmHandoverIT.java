package money.epsilon.brokerage.org;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** R-61: an RM leaves and someone takes over their clients, partners and RD regions from the next day. */
class RmHandoverIT extends IntegrationTestBase {

    MockHttpSession admin;
    long region;
    long branch;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        region = postJson("/api/regions", Map.of("name", uniqueName("R")), admin, 201).get("id").asLong();
        branch = postJson("/api/branches", Map.of("name", uniqueName("B"), "regionId", region, "from", "2026-04-01"), admin, 201).get("id").asLong();
    }

    long rm(String type) throws Exception {
        long id = postJson("/api/employees", Map.of("empCode", uniqueName("E"), "name", uniqueName("Person"), "type", type, "joinedOn", "2026-01-01"),
                admin, 201).get("id").asLong();
        postJson("/api/employees/" + id + "/branch", Map.of("branchId", branch, "from", "2026-04-01"), admin, 204);
        return id;
    }

    long client(long rmId) throws Exception {
        return postJson("/api/clients", Map.of("name", uniqueName("CLIENT"), "rmId", rmId, "from", "2026-09-01"), admin, 201).get("id").asLong();
    }

    long partnerOf(long rmId) throws Exception {
        long p = postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", uniqueName("SB")), admin, 201).get("id").asLong();
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rmId, "from", "2026-09-01"), admin, 201);
        return p;
    }

    @Test
    void anRmWhoLeavesHandsClientsPartnersAndRdToTheReplacementFromTheNextDay() throws Exception {
        long leaver = rm("RM");
        long next = rm("RM");
        postJson("/api/regions/" + region + "/rd", Map.of("employeeId", leaver, "from", "2026-09-01"), admin, 204);
        long c1 = client(leaver);
        long c2 = client(leaver);
        long p = partnerOf(leaver);

        JsonNode res = postJson("/api/employees/" + leaver + "/hand-over",
                Map.of("lastDay", "2026-10-15", "replacementId", next, "reason", "Resigned"), admin, 200);
        assertThat(res.get("leaver").get("status").asText()).isEqualTo("EXITED");
        assertThat(res.get("rdRegionIds").get(0).asLong()).isEqualTo(region);

        for (long c : new long[] {c1, c2}) {
            JsonNode history = getJson("/api/clients/" + c + "/rm", admin, 200);
            assertThat(history.get(0).get("targetId").asLong()).isEqualTo(next);
            assertThat(history.get(0).get("validFrom").asText()).isEqualTo("2026-10-16");
            assertThat(history.get(1).get("validTo").asText()).isEqualTo("2026-10-15");
        }
        JsonNode links = getJson("/api/partners/" + p + "/rm-links", admin, 200);
        assertThat(links).hasSize(2);
        assertThat(links.findValues("rmId").stream().map(JsonNode::asLong)).contains(leaver, next);
        JsonNode rd = getJson("/api/regions/" + region + "/rd", admin, 200);
        assertThat(rd.get(0).get("targetId").asLong()).isEqualTo(next);
        assertThat(rd.get(0).get("validFrom").asText()).isEqualTo("2026-10-16");
    }

    @Test
    void aNewRmCanBeMadeInTheLeaversBranchAndNothingChangesWhenItIsRefused() throws Exception {
        long leaver = rm("RM");
        long c = client(leaver);

        Map<String, Object> body = new HashMap<>(Map.of("lastDay", "2026-10-15", "newRmName", uniqueName("NEWRM")));
        JsonNode res = postJson("/api/employees/" + leaver + "/hand-over", body, admin, 200);
        long next = res.get("replacement").get("id").asLong();
        assertThat(res.get("replacement").get("branchId").asLong()).isEqualTo(branch);
        assertThat(getJson("/api/clients/" + c + "/rm", admin, 200).get(0).get("targetId").asLong()).isEqualTo(next);

        // a house book cannot take over an RD role: refused, and the RM stays employed with their clients
        long rdLeaver = rm("RM");
        long house = rm("HOUSE");
        postJson("/api/regions/" + region + "/rd", Map.of("employeeId", rdLeaver, "from", "2026-09-01"), admin, 204);
        long c2 = client(rdLeaver);
        postJson("/api/employees/" + rdLeaver + "/hand-over", Map.of("lastDay", "2026-10-15", "replacementId", house), admin, 409);
        assertThat(getJson("/api/employees/" + rdLeaver, admin, 200).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(getJson("/api/clients/" + c2 + "/rm", admin, 200).get(0).get("targetId").asLong()).isEqualTo(rdLeaver);

        // without the RD role, clients can go to a house book
        postJson("/api/employees/" + rdLeaver + "/hand-over", Map.of("lastDay", "2026-10-15", "replacementId", house, "takeOverRd", false), admin, 200);
        assertThat(getJson("/api/clients/" + c2 + "/rm", admin, 200).get(0).get("targetId").asLong()).isEqualTo(house);
    }
}
