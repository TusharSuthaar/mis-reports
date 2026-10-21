package money.epsilon.brokerage.nps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** R-86: the NPS screen. */
class NpsIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "NPR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "NPB " + tag, "regionId", region, "from", "2023-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "NPRM " + tag, "type", "RM", "branchId", branch, "from", "2023-01-01"), admin, 201).get("id").asLong();
    }

    @Test
    void anNpsInvestmentIsEnteredWithItsRevenueFromPopChargesTimesSharing() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("newClientName", "NPS CLIENT " + tag);
        body.put("newClientPan", FinesseFiles.pan(Long.parseLong(tag) * 10 + 9));
        body.put("month", "2025-11-01");
        body.put("rmId", rm);
        body.put("pop", "ICICI NPS");
        body.put("investmentDate", "2025-11-10");
        body.put("amount", 400000);
        body.put("welcomeMail", true);
        body.put("onPortal", true);
        body.put("revenueRemarks", "sharing will 60% of POP Charges");
        body.put("popCharges", 2000);
        body.put("sharePercent", 60);
        JsonNode e = postJson("/api/nps", body, admin, 201);
        assertThat(e.get("revenue").decimalValue()).isEqualByComparingTo("1200");   // 2,000 × 60%
        assertThat(e.get("gst").decimalValue()).isEqualByComparingTo("216");        // CGST 108 + SGST 108
        assertThat(e.get("invoice").decimalValue()).isEqualByComparingTo("1416");

        assertThat(getJson("/api/nps?q=" + tag + "&payment=PENDING", admin, 200)).hasSize(1);   // revenue, not received yet
        body.put("receivedOn", "2026-01-29");
        putJson("/api/nps/" + e.get("id").asLong(), body, admin, 200);
        assertThat(getJson("/api/nps?q=" + tag + "&payment=RECEIVED", admin, 200)).hasSize(1);

        // Typed revenue when there is no POP charge; none at all = 0.
        Map<String, Object> typed = new HashMap<>(body);
        typed.put("clientId", e.get("clientId").asLong());
        typed.remove("popCharges");
        typed.remove("sharePercent");
        typed.put("revenue", 213.5);
        assertThat(postJson("/api/nps", typed, admin, 201).get("invoice").decimalValue()).isEqualByComparingTo("251.93");

        byte[] xlsx = mvc.perform(get("/api/nps/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheet("NPS");
            assertThat(sheet.getRow(0).getCell(11).getStringCellValue()).isEqualTo("PARTNER");
            assertThat(sheet.getRow(0).getCell(21).getStringCellValue()).isEqualTo("CGST");
        }
        // NPS is a product partners can share in.
        long partner = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "NPS REF " + tag), admin, 201).get("id").asLong();
        postJson("/api/partners/" + partner + "/products", Map.of("product", "NPS", "percent", 10, "from", "2025-11-01"), admin, 204);
    }
}
