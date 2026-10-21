package money.epsilon.brokerage.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

/** R-08: the client's page shows everything the Ops client master has for it. */
class OpsMasterIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    private static Map<String, String> fields(JsonNode list) {
        Map<String, String> m = new HashMap<>();
        list.forEach(f -> m.put(f.get("label").asText(), f.get("value").asText()));
        return m;
    }

    @Test
    void theClientsPageGetsEveryFilledColumnAndAddressOfItsOpsRecord() throws Exception {
        MockHttpSession admin = loginAdmin();
        long n = System.nanoTime() % 1000000L;
        String pan = FinesseFiles.pan(n * 10 + 7);
        String joint = FinesseFiles.pan(n * 10 + 8);
        String code = "OPS" + n;
        uploadClientMaster(new FinesseFiles.ClientMaster().client(code, "OPS CLIENT " + n, pan, null)
                .contact("ops" + n + "@example.com", "9000000000", "01-01-1980", joint)
                .address(code, "12 Test Road", "Near Test Park", "Patna", "800001").bytes(), admin);

        long region = postJson("/api/regions", Map.of("name", uniqueName("OPR")), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", uniqueName("OPB"), "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long rm = postJson("/api/employees", Map.of("name", uniqueName("OPRM"), "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long client = postJson("/api/clients", Map.of("name", "OPS CLIENT " + n, "pan", pan, "rmId", rm, "from", "2026-09-01"), admin, 201).get("id").asLong();

        JsonNode records = getJson("/api/clients/" + client + "/ops-master", admin, 200);
        assertThat(records).hasSize(1);
        JsonNode r = records.get(0);
        assertThat(r.get("code").asText()).isEqualTo(code);
        Map<String, String> details = fields(r.get("details"));
        assertThat(details).containsEntry("Email", "ops" + n + "@example.com").containsEntry("Phone Number", "9000000000")
                .containsEntry("DOB", "01-01-1980").containsEntry("Joint Holder 1 PAN", joint).containsEntry("Relationship with Family", "Self");
        assertThat(details).doesNotContainKeys("Client Code", "Pan Number", "SRM");   // own columns, and blank ones, are not repeated
        Map<String, String> address = fields(r.get("addresses").get(0));
        assertThat(address).containsEntry("Address Line", "12 Test Road").containsEntry("Address Line 2", "Near Test Park")
                .containsEntry("City", "Patna").containsEntry("PIN Code", "800001").doesNotContainKey("RM");

        // PANs are masked for roles that do not see them in full.
        JsonNode masked = getJson("/api/clients/" + client + "/ops-master", loginAs("HO"), 200).get(0);
        assertThat(masked.get("pan").asText()).contains("****");
        assertThat(fields(masked.get("details")).get("Joint Holder 1 PAN")).contains("****");

        // Stored encrypted: no e-mail in clear text in the table.
        assertThat(jdbc.sql("SELECT count(*) FROM finesse_client WHERE code = ? AND details_encrypted LIKE '%example.com%'").param(code)
                .query(Long.class).single()).isZero();
    }
}
