package money.epsilon.brokerage.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Finding a client by PAN, and correcting a PAN. */
class ClientPanIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag, panOne, panTwo, panNew;
    long one;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long t = Long.parseLong(tag);
        panOne = FinesseFiles.pan(t * 10 + 1);
        panTwo = FinesseFiles.pan(t * 10 + 2);
        panNew = FinesseFiles.pan(t * 10 + 3);
        long region = postJson("/api/regions", Map.of("name", "PR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "PB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long rm = postJson("/api/employees", Map.of("name", "PRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        one = masterClient("PAN ONE " + tag, panOne, rm, "2026-01-01");
        masterClient("PAN TWO " + tag, panTwo, rm, "2026-01-01");
    }

    @Test
    void aClientIsFoundByItsFullPanInAnyCase() throws Exception {
        JsonNode found = getJson("/api/clients?q=" + panOne.toLowerCase(), admin, 200);
        assertThat(found.get("items")).singleElement().satisfies(c -> assertThat(c.get("id").asLong()).isEqualTo(one));
        assertThat(getJson("/api/clients?q=" + panOne.substring(0, 6), admin, 200).get("total").asInt()).isZero(); // part of a PAN finds nothing
    }

    @Test
    void aPanIsCorrectedAndAudited() throws Exception {
        postJson("/api/clients/" + one + "/pan", Map.of("pan", "not-a-pan"), admin, 400);
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panTwo), admin, 409);     // another client has it
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panOne), admin, 409);     // unchanged
        postJson("/api/clients/" + one + "/pan", Map.of("pan", " " + panNew.toLowerCase() + " ", "reason", "typo in master"), admin, 204);

        assertThat(getJson("/api/clients?q=" + panNew, admin, 200).get("items").get(0).get("id").asLong()).isEqualTo(one);
        assertThat(getJson("/api/clients?q=" + panOne, admin, 200).get("total").asInt()).isZero();
        assertThat(getJson("/api/clients/" + one, admin, 200).get("client").get("pan").asText()).isEqualTo(panNew);
        JsonNode audit = getJson("/api/audit?entity=CLIENT&size=5", admin, 200).get("items").get(0);
        assertThat(audit.get("action").asText()).isEqualTo("PAN_CHANGE");
        assertThat(audit.toString()).contains(panNew.substring(0, 5) + "****").doesNotContain(panNew);
    }

    @Test
    void aPanOnADuplicateWithoutRmIsMovedToTheRightClient() throws Exception {
        // A file created a second record with the correct PAN before the master was fixed: no RM on it.
        long duplicate = clientService.createUncoded("PAN ONE " + tag, panNew);
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panNew), admin, 409);                          // refused unless asked
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panTwo, "takeFromOther", true), admin, 409);  // PAN TWO has an RM
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panNew, "reason", "file PAN is right", "takeFromOther", true), admin, 204);

        assertThat(getJson("/api/clients/" + one, admin, 200).get("client").get("pan").asText()).isEqualTo(panNew);
        assertThat(getJson("/api/clients/" + duplicate, admin, 200).get("client").get("pan").isNull()).isTrue();
        JsonNode audit = getJson("/api/audit?entity=CLIENT&entityId=" + duplicate + "&size=5", admin, 200).get("items").get(0);
        assertThat(audit.toString()).contains("PAN moved to client " + one).doesNotContain(panNew);
    }

    @Test
    void financeCannotChangeAPan() throws Exception {
        postJson("/api/clients/" + one + "/pan", Map.of("pan", panNew), loginAs("FINANCE"), 403);
    }
}
