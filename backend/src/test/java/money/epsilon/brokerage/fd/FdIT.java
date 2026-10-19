package money.epsilon.brokerage.fd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class FdIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag, pan;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 4);
        long region = postJson("/api/regions", Map.of("name", "FDR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "FDB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "FDRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
    }

    private Map<String, Object> entry(String month) {
        Map<String, Object> m = new HashMap<>();
        m.put("month", month);
        m.put("rmId", rm);
        m.put("fdName", "Test Finance FD");
        m.put("fdNo", "FD-" + tag);
        m.put("interestFrequency", "CUMULATIVE");
        m.put("platform", "Test Platform");
        m.put("depositDate", month);
        m.put("periodMonths", 36);
        m.put("maturityDate", "2029-08-01");
        m.put("ratePercent", 8.25);
        m.put("amount", 1000000);
        m.put("certificate", "PENDING");
        m.put("marginPercent", 1);
        m.put("tdsApplied", true);
        m.put("tdsPercent", 2);
        return m;
    }

    @Test
    void anFdIsEnteredForANewClientAndItsRevenueWorkedOut() throws Exception {
        Map<String, Object> body = entry("2026-08-01");
        body.put("newClientName", "FD CLIENT " + tag);
        body.put("newClientPan", pan);
        body.put("mobile", "9999999999");

        JsonNode fd = postJson("/api/fd", body, admin, 201);
        assertThat(fd.get("gross").decimalValue()).isEqualByComparingTo("10000");
        assertThat(fd.get("tds").decimalValue()).isEqualByComparingTo("200");
        assertThat(fd.get("gst").decimalValue()).isEqualByComparingTo("1800");
        assertThat(fd.get("net").decimalValue()).isEqualByComparingTo("9800");
        assertThat(fd.get("invoice").decimalValue()).isEqualByComparingTo("11800");
        // Cumulative, yearly by default: 8.25% for 36 months (R-72).
        assertThat(fd.get("compounding").asText()).isEqualTo("YEARLY");
        assertThat(fd.get("maturityValue").decimalValue()).isEqualByComparingTo("1268480.27");
        assertThat(fd.get("clientType").asText()).isEqualTo("OFFLINE");
        // The next FD with the same company, platform and period is offered the same margin.
        JsonNode suggestions = getJson("/api/fd/suggestions", admin, 200);
        assertThat(suggestions.get("platforms").toString()).contains("Test Platform");
        assertThat(suggestions.get("margins").toString()).contains("\"periodMonths\":36");
        assertThat(fd.get("region").asText()).isEqualTo("FDR " + tag);
        assertThat(fd.get("paymentStatus").asText()).isEqualTo("PENDING");
        // The new client is on the Clients screen with the FD's RM.
        JsonNode client = getJson("/api/clients?q=" + pan, admin, 200).get("items").get(0);
        assertThat(client.get("rmId").asLong()).isEqualTo(rm);

        // Payment received later: an edit, audited.
        long id = fd.get("id").asLong();
        Map<String, Object> edit = entry("2026-08-01");
        edit.put("paymentStatus", "RECEIVED_GST_PENDING");
        edit.put("invoiceRaised", true);
        edit.put("onPortal", true);
        JsonNode edited = putJson("/api/fd/" + id, edit, admin, 200);
        assertThat(edited.get("paymentStatus").asText()).isEqualTo("RECEIVED_GST_PENDING");
        assertThat(edited.get("onPortal").asBoolean()).isTrue();
        // The month as the FD register in Excel.
        byte[] xlsx = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/fd/export.xlsx?month=2026-08").session(admin))
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("SR.NO");
            boolean found = false;
            for (var row : sheet) found |= row.getRowNum() > 0 && ("FD-" + tag).equals(row.getCell(13).getStringCellValue());
            assertThat(found).isTrue();
        }
        assertThat(getJson("/api/fd?from=2026-08&to=2026-08", admin, 200).toString()).contains("FD-" + tag);
        JsonNode audit = getJson("/api/audit?entity=FD_ENTRY&entityId=" + id + "&size=5", admin, 200).get("items").get(0);
        assertThat(audit.get("action").asText()).isEqualTo("UPDATE");
        assertThat(audit.toString()).doesNotContain(pan);

        postJson("/api/fd/" + id + "/cancel", Map.of("reason", "booked twice"), admin, 204);
        assertThat(getJson("/api/fd/" + id, admin, 200).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void theListShowsAllMonthsAndNarrowsByFilters() throws Exception {
        Map<String, Object> aug = entry("2026-08-01");
        aug.put("newClientName", "FILTERAUG" + tag);
        long augId = postJson("/api/fd", aug, admin, 201).get("id").asLong();
        Map<String, Object> sep = entry("2026-09-01");
        sep.put("newClientName", "FILTERSEP" + tag);
        sep.put("platform", "OtherPlatform" + tag);
        long sepId = postJson("/api/fd", sep, admin, 201).get("id").asLong();

        String both = getJson("/api/fd?rmId=" + rm, admin, 200).toString();
        assertThat(both).contains("\"id\":" + augId).contains("\"id\":" + sepId);                       // no month: every month
        assertThat(getJson("/api/fd?rmId=" + rm + "&from=2026-09", admin, 200).toString())
                .contains("\"id\":" + sepId).doesNotContain("\"id\":" + augId + ",");                    // month range
        assertThat(getJson("/api/fd?q=filteraug" + tag, admin, 200)).hasSize(1);                        // text, any case
        assertThat(getJson("/api/fd?rmId=" + rm + "&platform=otherplatform" + tag, admin, 200)).hasSize(1);
        assertThat(getJson("/api/fd?rmId=" + rm + "&payment=RECEIVED", admin, 200)).isEmpty();
        postJson("/api/fd/" + sepId + "/cancel", Map.of("reason", "test"), admin, 204);
        assertThat(getJson("/api/fd?rmId=" + rm, admin, 200)).hasSize(1);                                  // cancelled hidden
        assertThat(getJson("/api/fd?rmId=" + rm + "&includeCancelled=true", admin, 200)).hasSize(2);
    }

    @Test
    void registerFiguresAreKeptExactlyAsGiven() throws Exception {
        Map<String, Object> body = entry("2026-08-01");
        body.put("newClientName", "GIVEN " + tag);
        body.put("figuresAsGiven", true);
        body.put("givenGross", 5932.20);
        body.put("givenGst", 1067.80);
        body.put("givenInvoice", 7000);
        JsonNode fd = postJson("/api/fd", body, admin, 201);
        // Not 10,000 (1% of 10 lakh): the register's own figures, as they are.
        assertThat(fd.get("figuresAsGiven").asBoolean()).isTrue();
        assertThat(fd.get("gross").decimalValue()).isEqualByComparingTo("5932.20");
        assertThat(fd.get("tds").decimalValue()).isEqualByComparingTo("0");
        assertThat(fd.get("net").decimalValue()).isEqualByComparingTo("5932.20");
        assertThat(fd.get("invoice").decimalValue()).isEqualByComparingTo("7000");
        // Untick: worked out again.
        assertThat(putJson("/api/fd/" + fd.get("id").asLong(), entry("2026-08-01"), admin, 200).get("gross").decimalValue()).isEqualByComparingTo("10000");
        body.put("givenTds", 9999);
        postJson("/api/fd", body, admin, 400);   // TDS above gross
    }

    @Test
    void taxRatesAreDatedPerProductAndAnEntryUsesItsMonthsRate() throws Exception {
        postJson("/api/product-taxes/FD", Map.of("gstPercent", 12, "tdsPercent", 5, "tdsByDefault", true, "from", "2027-04-01",
                "reason", "test rate"), admin, 204);
        assertThat(getJson("/api/product-taxes/FD/on?date=2027-03-31", admin, 200).get("gstPercent").decimalValue()).isEqualByComparingTo("18");
        JsonNode april = getJson("/api/product-taxes/FD/on?date=2027-04-01", admin, 200);
        assertThat(april.get("gstPercent").decimalValue()).isEqualByComparingTo("12");
        assertThat(april.get("tdsByDefault").asBoolean()).isTrue();

        Map<String, Object> body = entry("2027-04-01");
        body.put("newClientName", "FD APRIL " + tag);
        assertThat(postJson("/api/fd", body, admin, 201).get("gst").decimalValue()).isEqualByComparingTo("1200");
        // No rate before GST (1 Jul 2017, V28): refused rather than guessed.
        Map<String, Object> early = entry("2017-06-01");
        early.put("newClientName", "FD EARLY " + tag);
        postJson("/api/fd", early, admin, 409);
        // Finance may read but not change rates.
        postJson("/api/product-taxes/FD", Map.of("gstPercent", 18, "tdsPercent", 0, "from", "2027-05-01"), loginAs("FINANCE"), 403);
    }
}
