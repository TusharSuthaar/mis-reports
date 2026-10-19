package money.epsilon.brokerage.aif;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AifIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag, pan;
    long rm, platform, referral;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 7);
        long region = postJson("/api/regions", Map.of("name", "AFR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "AFB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "AFRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        platform = postJson("/api/platforms", Map.of("name", "WEALTH " + tag), admin, 201).get("id").asLong();
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", false, "tdsPercent", 2, "ourShare", 100, "from", "2026-01-01"), admin, 204);
        referral = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "AIF REF " + tag), admin, 201).get("id").asLong();
    }

    private Map<String, Object> deal() {
        Map<String, Object> m = new HashMap<>();
        m.put("month", "2026-07-01");
        m.put("rmId", rm);
        m.put("platformId", platform);
        m.put("fundName", "TEST VALUE FUND " + tag);
        m.put("commitment", 10000000);
        m.put("agreementDate", "2026-07-23");
        m.put("tenure", "Five Years");
        m.put("markupPercent", 1.3);
        m.put("tdsApplied", true);
        m.put("tdsPercent", 2);
        m.put("payoutTerms", "Paid quarterly - Oct");
        m.put("instalments", List.of(Map.of("amount", 2000000, "paidOn", "2026-07-27"), Map.of("amount", 2000000, "paidOn", "2026-08-31")));
        return m;
    }

    @Test
    void anAifCommitmentIsEnteredWithItsInstalmentsAndRevenue() throws Exception {
        Map<String, Object> body = deal();
        body.put("newClientName", "AIF CLIENT " + tag);
        body.put("newClientPan", pan);
        body.put("partnerId", referral);
        body.put("sharingPercent", 50);
        JsonNode aif = postJson("/api/aif", body, admin, 201);
        assertThat(aif.get("gross").decimalValue()).isEqualByComparingTo("130000");
        assertThat(aif.get("tds").decimalValue()).isEqualByComparingTo("2600");
        assertThat(aif.get("gst").decimalValue()).isEqualByComparingTo("23400");
        assertThat(aif.get("net").decimalValue()).isEqualByComparingTo("127400");
        assertThat(aif.get("invoice").decimalValue()).isEqualByComparingTo("153400");
        assertThat(aif.get("instalments")).hasSize(2);
        assertThat(aif.get("called").decimalValue()).isEqualByComparingTo("4000000");
        assertThat(aif.get("sharingPercent").decimalValue()).isEqualByComparingTo("50");

        // Instalments may not add up to more than the commitment.
        Map<String, Object> tooMuch = deal();
        tooMuch.put("clientId", aif.get("clientId").asLong());
        tooMuch.put("instalments", List.of(Map.of("amount", 20000000)));
        postJson("/api/aif", tooMuch, admin, 400);

        // A third drawdown later, and the partner paid: an edit.
        long id = aif.get("id").asLong();
        Map<String, Object> edit = deal();
        edit.put("partnerId", referral);
        edit.put("sharingPercent", 50);
        edit.put("partnerPaid", true);
        edit.put("instalments", List.of(Map.of("amount", 2000000, "paidOn", "2026-07-27"), Map.of("amount", 2000000, "paidOn", "2026-08-31"),
                Map.of("amount", 1500000, "paidOn", "2026-10-15")));
        JsonNode edited = putJson("/api/aif/" + id, edit, admin, 200);
        assertThat(edited.get("instalments")).hasSize(3);
        assertThat(edited.get("partnerPaid").asBoolean()).isTrue();
        assertThat(getJson("/api/aif?q=" + "TEST", admin, 200).toString()).contains("TEST VALUE FUND " + tag);
        assertThat(getJson("/api/aif/suggestions", admin, 200).toString()).contains("TEST VALUE FUND " + tag);
        assertThat(getJson("/api/audit?entity=AIF_ENTRY&entityId=" + id + "&size=5", admin, 200).toString()).doesNotContain(pan);

        byte[] xlsx = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/aif/export.xlsx").session(admin))
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            boolean found = false;
            for (var row : wb.getSheetAt(0)) found |= row.getRowNum() > 0 && ("TEST VALUE FUND " + tag).equals(row.getCell(12).getStringCellValue());
            assertThat(found).isTrue();
        }
    }
}
