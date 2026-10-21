package money.epsilon.brokerage.gift;

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

/** R-82: the GIFT CITY screen. */
class GiftIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "GCR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "GCB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "GCRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
    }

    @Test
    void aGiftCityInvestmentIsEnteredWithItsRupeeAmountAndBrokerage() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("newClientName", "GIFT CLIENT " + tag);
        body.put("newClientPan", FinesseFiles.pan(Long.parseLong(tag) * 10 + 3));
        body.put("month", "2026-09-01");
        body.put("rmId", rm);
        body.put("fund", "DSP GIFT CITY");
        body.put("amountUsd", 10000);
        body.put("fxRate", 95.97);
        body.put("fxRateDate", "2026-09-25");
        body.put("fxSource", "ECB rate of 2026-09-25");
        body.put("onPortal", true);
        body.put("trxnStatus", "DONE");
        JsonNode e = postJson("/api/gift", body, admin, 201);
        assertThat(e.get("amountInr").decimalValue()).isEqualByComparingTo("959700");
        assertThat(e.get("fxSource").asText()).isEqualTo("ECB rate of 2026-09-25");
        assertThat(e.get("gross").decimalValue()).isEqualByComparingTo("0");   // no brokerage yet
        assertThat(e.get("region").asText()).isEqualTo("GCR " + tag);
        long id = e.get("id").asLong();

        // The brokerage, once known: GST from the GIFT rate (18% to start), TDS when ticked.
        body.put("brokerage", 5000);
        body.put("tdsApplied", true);
        body.put("tdsPercent", 2);
        JsonNode withBrokerage = putJson("/api/gift/" + id, body, admin, 200);
        assertThat(withBrokerage.get("gst").decimalValue()).isEqualByComparingTo("900");
        assertThat(withBrokerage.get("tds").decimalValue()).isEqualByComparingTo("100");
        assertThat(withBrokerage.get("net").decimalValue()).isEqualByComparingTo("4900");
        assertThat(withBrokerage.get("invoice").decimalValue()).isEqualByComparingTo("5900");

        // Neither amount: refused.
        Map<String, Object> none = new HashMap<>(body);
        none.remove("amountUsd");
        none.put("clientId", e.get("clientId").asLong());
        postJson("/api/gift", none, admin, 400);

        assertThat(getJson("/api/gift?q=" + tag, admin, 200)).hasSize(1);
        assertThat(getJson("/api/gift/funds", admin, 200).toString()).contains("DSP GIFT CITY");
        byte[] xlsx = mvc.perform(get("/api/gift/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheet("GIFT CITY");
            assertThat(sheet.getRow(0).getCell(11).getStringCellValue()).isEqualTo("AMT");
            assertThat(sheet.getRow(1).getCell(11).getStringCellValue()).isEqualTo("USD 10000");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(959700.0);
            assertThat(sheet.getRow(1).getCell(14).getStringCellValue()).isEqualTo("Done");
        }
    }
}
