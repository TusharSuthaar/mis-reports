package money.epsilon.brokerage.pms;

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

/** R-85: PMS investments and AMC payouts (entry only). */
class PmsIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "PMR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "PMB " + tag, "regionId", region, "from", "2023-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "PMRM " + tag, "type", "RM", "branchId", branch, "from", "2023-01-01"), admin, 201).get("id").asLong();
    }

    @Test
    void aPmsInvestmentIsEnteredAsInTheRegisterAndCanBeRedeemed() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("newClientName", "PMS CLIENT " + tag);
        body.put("newClientPan", FinesseFiles.pan(Long.parseLong(tag) * 10 + 5));
        body.put("month", "2026-06-01");
        body.put("rmId", rm);
        body.put("amc", "ICICI PRU");
        body.put("strategy", "ICICI PIPE - STOCK TRF");
        body.put("amount", 1300000);
        body.put("investmentDate", "2026-06-30");
        body.put("onPortal", true);
        body.put("trailPercent", 1.25);
        body.put("payoutFrequency", "QUARTERLY");
        body.put("qty", 100);
        body.put("price", 1250.5);
        JsonNode e = postJson("/api/pms", body, admin, 201);
        assertThat(e.get("consideration").decimalValue()).isEqualByComparingTo("125050");   // qty × price
        assertThat(e.get("region").asText()).isEqualTo("PMR " + tag);
        assertThat(e.get("holdingStatus").asText()).isEqualTo("ACTIVE");
        long id = e.get("id").asLong();

        body.put("holdingStatus", "REDEEMED");
        putJson("/api/pms/" + id, body, admin, 400);   // needs the date
        body.put("redeemedOn", "2026-07-15");
        assertThat(putJson("/api/pms/" + id, body, admin, 200).get("redeemedOn").asText()).isEqualTo("2026-07-15");

        assertThat(getJson("/api/pms?q=" + tag, admin, 200)).hasSize(1);
        assertThat(getJson("/api/pms?q=" + tag + "&payment=PENDING", admin, 200)).isEmpty();   // no revenue on PMS entries yet
        assertThat(getJson("/api/pms/suggestions", admin, 200).get("amcs").toString()).contains("ICICI PRU");
        byte[] xlsx = mvc.perform(get("/api/pms/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheet("PMS");
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("PARTNER");
            assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEqualTo("REDEEMED ON JUL-26");
            assertThat(sheet.getRow(1).getCell(23).getNumericCellValue()).isEqualTo(0.0125);
        }
    }

    @Test
    void anAmcPayoutIsKeptPerInvoicePeriodWithTdsAndGst() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("amc", "MOTILAL AMC " + tag);
        body.put("periodFrom", "2026-04-01");
        body.put("periodTo", "2026-06-01");
        body.put("amount", 5565);
        body.put("tdsApplied", true);
        body.put("tdsPercent", 2);
        body.put("receivedOn", "2026-07-21");
        JsonNode p = postJson("/api/pms/payouts", body, admin, 201);
        assertThat(p.get("tds").decimalValue()).isEqualByComparingTo("111.30");
        assertThat(p.get("gst").decimalValue()).isEqualByComparingTo("1001.70");
        assertThat(p.get("net").decimalValue()).isEqualByComparingTo("5453.70");
        assertThat(p.get("invoice").decimalValue()).isEqualByComparingTo("6566.70");
        assertThat(getJson("/api/pms/payouts?from=2026-04&to=2026-04", admin, 200).toString()).contains("MOTILAL AMC " + tag);

        body.put("periodTo", "2026-03-01");
        postJson("/api/pms/payouts", body, admin, 400);   // ends before it starts
        postJson("/api/pms/payouts/" + p.get("id").asLong() + "/cancel", Map.of("reason", "test"), admin, 204);
        assertThat(getJson("/api/pms/payouts?from=2026-04&to=2026-04", admin, 200).toString()).doesNotContain("MOTILAL AMC " + tag);
    }
}
