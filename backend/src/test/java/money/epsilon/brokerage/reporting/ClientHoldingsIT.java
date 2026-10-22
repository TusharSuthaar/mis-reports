package money.epsilon.brokerage.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.equity.EquityFiles;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** R-09: the client page's holdings are everything the client's PAN holds. */
class ClientHoldingsIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "HLR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "HLB " + tag, "regionId", region, "from", "2023-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "HLRM " + tag, "type", "RM", "branchId", branch, "from", "2023-01-01"), admin, 201).get("id").asLong();
    }

    private void nps(long clientId, int amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("clientId", clientId);
        body.put("month", "2025-11-01");
        body.put("rmId", rm);
        body.put("pop", "HL POP " + tag);
        body.put("investmentDate", "2025-11-10");
        body.put("amount", amount);
        body.put("revenue", 100);
        postJson("/api/nps", body, admin, 201);
    }

    @Test
    void theHoldingsOfAPanTakeInItsOtherRecordsItsMinorsAndEquityTradesNotLinkedYet() throws Exception {
        String pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 1);
        String otherPan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 2);

        // Equity traded under the PAN before any client had it: matched by PAN.
        int y = 2400 + (int) (Long.parseLong(tag) % 500);
        mvc.perform(withCsrf(multipart("/api/equity/upload").file(new MockMultipartFile("file", "Equity Source.xlsx", null,
                        new EquityFiles().trade("H" + tag, pan, "HL CLIENT", LocalDate.of(y, 3, 3), "N", 100, 80).bytes()))).session(admin))
                .andExpect(status().isOk());

        long client = postJson("/api/clients", Map.of("name", "HL CLIENT " + tag, "pan", pan, "rmId", rm, "from", "2025-01-01"), admin, 201).get("id").asLong();
        long duplicate = masterClient("HL DUPLICATE " + tag, pan, rm, "2025-01-01");
        long minor = clientService.create("FINESSE", "HM" + tag, null, "HL MINOR " + tag, null, null);
        uploadClientMaster(new FinesseFiles.ClientMaster().client("HM" + tag, "HL MINOR " + tag, null, pan).bytes(), admin);
        long stranger = postJson("/api/clients", Map.of("name", "HL STRANGER " + tag, "pan", otherPan, "rmId", rm, "from", "2025-01-01"), admin, 201)
                .get("id").asLong();
        nps(client, 1000);
        nps(duplicate, 2000);
        nps(minor, 3000);
        nps(stranger, 4000);

        JsonNode h = getJson("/api/clients/" + client + "/holdings", admin, 200);
        List<String> members = new ArrayList<>();
        h.get("members").forEach(m -> members.add(m.get("clientId").asLong() + ":" + m.get("relation").asText()));
        assertThat(members).containsExactly(client + ":THIS", duplicate + ":SAME_PAN", minor + ":MINOR");
        List<String> rows = new ArrayList<>();
        h.get("rows").forEach(r -> rows.add(r.get("product").asText() + ":" + r.get("amount").asText()));
        assertThat(rows).containsExactlyInAnyOrder("Equity:null", "NPS:1000.0", "NPS:2000.0", "NPS:3000.0");
        JsonNode equity = h.get("rows").get(0);   // newest month first
        assertThat(equity.get("product").asText()).isEqualTo("Equity");
        assertThat(equity.get("gross").decimalValue()).isEqualByComparingTo("80");

        // The minor has no PAN of its own: only its own entries.
        JsonNode m = getJson("/api/clients/" + minor + "/holdings", admin, 200);
        assertThat(m.get("members")).hasSize(1);
        assertThat(m.get("rows")).hasSize(1);
        assertThat(m.get("rows").get(0).get("amount").decimalValue()).isEqualByComparingTo("3000");
    }
}
