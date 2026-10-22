package money.epsilon.brokerage.p2p;

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

/** R-87: the P2P screen. */
class P2pIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "PPR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "PPB " + tag, "regionId", region, "from", "2023-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "PPRM " + tag, "type", "RM", "branchId", branch, "from", "2023-01-01"), admin, 201).get("id").asLong();
    }

    @Test
    void aP2pInvestmentIsEnteredAsInTheRegisterWithItsBrokeragewhenKnown() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("newClientName", "P2P CLIENT " + tag);
        body.put("newClientPan", FinesseFiles.pan(Long.parseLong(tag) * 10 + 1));
        body.put("month", "2023-07-01");
        body.put("rmId", rm);
        body.put("platform", "INDIA MONEY MART");
        body.put("scheme", "YEARLY");
        body.put("roiPercent", 13);
        body.put("amount", 300000);
        body.put("fundsInitiatedOn", "2023-07-19");
        body.put("startDate", "2023-09-01");
        body.put("quarterEndDate", "2023-09-30");
        body.put("onPortal", true);
        body.put("paymentNote", "BILL RAISED -13 OCT");
        JsonNode e = postJson("/api/p2p", body, admin, 201);
        assertThat(e.get("gross").decimalValue()).isEqualByComparingTo("0");   // no brokerage known yet
        assertThat(e.get("region").asText()).isEqualTo("PPR " + tag);

        body.put("brokerage", 1500);
        body.put("tdsApplied", true);
        body.put("tdsPercent", 2);
        JsonNode b = putJson("/api/p2p/" + e.get("id").asLong(), body, admin, 200);
        assertThat(b.get("tds").decimalValue()).isEqualByComparingTo("30");
        assertThat(b.get("gst").decimalValue()).isEqualByComparingTo("270");
        assertThat(b.get("invoice").decimalValue()).isEqualByComparingTo("1770");
        assertThat(getJson("/api/p2p?q=" + tag + "&payment=PENDING", admin, 200)).hasSize(1);
        assertThat(getJson("/api/p2p/suggestions", admin, 200).get("platforms").toString()).contains("INDIA MONEY MART");

        byte[] xlsx = mvc.perform(get("/api/p2p/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheet("P2P");
            assertThat(sheet.getRow(0).getCell(11).getStringCellValue()).isEqualTo("P2P SCHEME");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(0.13);
            assertThat(sheet.getRow(1).getCell(18).getStringCellValue()).isEqualTo("BILL RAISED -13 OCT");
        }
    }
}
