package money.epsilon.brokerage.bond;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

class BondIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;
    String tag, pan;
    long rm, platform;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 6);
        long region = postJson("/api/regions", Map.of("name", "BDR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "BDB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "BDRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        platform = postJson("/api/platforms", Map.of("name", "PLATFORM " + tag), admin, 201).get("id").asLong();
        // GST-inclusive, TDS 5%, Epsilon gets 67% of the mark-up; from August all of it.
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", true, "tdsPercent", 5, "ourShare", 67, "from", "2026-01-01"), admin, 204);
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", true, "tdsPercent", 5, "ourShare", 100, "from", "2026-08-01"), admin, 204);
    }

    private Map<String, Object> deal(String month) {
        Map<String, Object> m = new HashMap<>();
        m.put("month", month);
        m.put("rmId", rm);
        m.put("platformId", platform);
        m.put("bondName", "TEST STATE BEVERAGES " + tag);
        m.put("isin", "inE0M" + tag);
        m.put("couponPercent", 9.62);
        m.put("payoutFrequency", "QUARTERLY");
        m.put("investmentDate", month);
        m.put("maturityDate", month.compareTo("2028") < 0 ? "2028-05-31" : java.time.LocalDate.parse(month).plusYears(2).toString());
        m.put("quantity", 1);
        m.put("ratePerUnit", 1014225.66);
        m.put("investmentAmount", 1000000);
        m.put("accruedInterest", 14225.66);
        m.put("markupPercent", 0.4);
        m.put("tdsApplied", true);
        m.put("tdsPercent", 5);
        return m;
    }

    @Test
    void aBondIsEnteredAndItsBrokerageWorkedOutFromThePlatformsTerms() throws Exception {
        Map<String, Object> body = deal("2026-07-01");
        body.put("newClientName", "BOND CLIENT " + tag);
        body.put("newClientPan", pan);
        JsonNode bond = postJson("/api/bonds", body, admin, 201);
        assertThat(bond.get("ourShare").decimalValue()).isEqualByComparingTo("67");
        assertThat(bond.get("invoice").decimalValue()).isEqualByComparingTo("2680");
        assertThat(bond.get("gross").decimalValue()).isEqualByComparingTo("2271.19");
        assertThat(bond.get("tds").decimalValue()).isEqualByComparingTo("113.56");
        assertThat(bond.get("consideration").decimalValue()).isEqualByComparingTo("1014225.66");
        // Bought between the record date and the coupon date: negative accrued interest lowers the consideration.
        Map<String, Object> exInterest = deal("2026-07-01");
        exInterest.put("clientId", bond.get("clientId").asLong());
        exInterest.put("accruedInterest", -2500.50);
        assertThat(postJson("/api/bonds", exInterest, admin, 201).get("consideration").decimalValue()).isEqualByComparingTo("997499.50");
        assertThat(bond.get("isin").asText()).isEqualTo("INE0M" + tag);
        assertThat(bond.get("clientType").asText()).isEqualTo("OFFLINE");
        assertThat(bond.get("platformName").asText()).isEqualTo("PLATFORM " + tag);
        assertThat(getJson("/api/clients?q=" + pan, admin, 200).get("items").get(0).get("rmId").asLong()).isEqualTo(rm);

        long id = bond.get("id").asLong();
        Map<String, Object> edit = deal("2026-07-01");
        edit.put("paymentStatus", "RECEIVED");
        edit.put("paymentReceivedOn", "2026-07-29");
        edit.put("invoiceRaised", true);
        assertThat(putJson("/api/bonds/" + id, edit, admin, 200).get("paymentReceivedOn").asText()).isEqualTo("2026-07-29");
        assertThat(getJson("/api/bonds/suggestions", admin, 200).toString()).contains("TEST STATE BEVERAGES " + tag);
        JsonNode audit = getJson("/api/audit?entity=BOND_ENTRY&entityId=" + id + "&size=5", admin, 200).get("items").get(0);
        assertThat(audit.toString()).doesNotContain(pan);

        byte[] xlsx = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bonds/export.xlsx?month=2026-07").session(admin))
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            boolean found = false;
            for (var row : wb.getSheetAt(0)) found |= row.getRowNum() > 0 && ("INE0M" + tag).equals(row.getCell(16).getStringCellValue());
            assertThat(found).isTrue();
        }
        postJson("/api/bonds/" + id + "/cancel", Map.of("reason", "test"), admin, 204);
        assertThat(getJson("/api/bonds/" + id, admin, 200).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void platformTermsAreDatedAndADealUsesItsMonthsTerms() throws Exception {
        assertThat(getJson("/api/platforms/" + platform + "/terms/on?date=2026-07-31", admin, 200).get("ourShare").decimalValue()).isEqualByComparingTo("67");
        assertThat(getJson("/api/platforms/" + platform + "/terms/on?date=2026-08-01", admin, 200).get("ourShare").decimalValue()).isEqualByComparingTo("100");
        Map<String, Object> aug = deal("2026-08-01");
        aug.put("newClientName", "BOND AUG " + tag);
        assertThat(postJson("/api/bonds", aug, admin, 201).get("invoice").decimalValue()).isEqualByComparingTo("4000");
        // Before the platform's first terms: refused, not guessed.
        Map<String, Object> early = deal("2025-12-01");
        early.put("newClientName", "BOND EARLY " + tag);
        postJson("/api/bonds", early, admin, 409);
        postJson("/api/platforms", Map.of("name", "platform " + tag), admin, 409);   // same name, any case
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", false, "tdsPercent", 5, "ourShare", 100, "from", "2026-09-01"),
                loginAs("FINANCE"), 403);
    }

    /** R-98: a LOCKED month refuses its entries (add, change, move in or out, cancel); a PROCESSED month goes back to OPEN. */
    @Test
    void aLockedMonthRefusesItsEntriesAndAProcessedMonthReopens() throws Exception {
        int y = 2400 + (int) (Long.parseLong(tag) % 500);   // months no other test uses
        String locked = y + "-03-01", processed = y + "-04-01", open = y + "-05-01";
        month(locked, "LOCKED");
        month(processed, "PROCESSED");
        try {
            lockedMonthChecks(y, locked, processed, open);
        } finally {
            // A locked month also refuses dated master changes before it (clients, RMs): leave none behind for other tests.
            jdbc.sql("UPDATE period SET status = 'OPEN' WHERE month IN (?, ?, ?)")
                    .params(java.time.LocalDate.parse(locked), java.time.LocalDate.parse(processed), java.time.LocalDate.parse(open)).update();
        }
    }

    private void lockedMonthChecks(int y, String locked, String processed, String open) throws Exception {
        Map<String, Object> body = deal(processed);
        body.put("newClientName", "LOCK CLIENT " + tag);
        body.put("newClientPan", pan);
        long client = postJson("/api/bonds", body, admin, 201).get("clientId").asLong();
        assertThat(status(processed)).isEqualTo("OPEN");   // its split and END must be made again

        Map<String, Object> inLocked = deal(locked);
        inLocked.put("clientId", client);
        assertThat(postJson("/api/bonds", inLocked, admin, 409).toString()).contains("Mar " + y + " is locked");

        Map<String, Object> inOpen = deal(open);
        inOpen.put("clientId", client);
        long id = postJson("/api/bonds", inOpen, admin, 201).get("id").asLong();
        putJson("/api/bonds/" + id, deal(locked), admin, 409);             // moving it into a locked month
        month(open, "LOCKED");
        putJson("/api/bonds/" + id, deal(open), admin, 409);               // changing it inside one
        postJson("/api/bonds/" + id + "/cancel", Map.of("reason", "test"), admin, 409);
        assertThat(getJson("/api/bonds?from=" + y + "-05&to=" + y + "-05", admin, 200).get(0).get("status").asText()).isEqualTo("ACTIVE");
    }

    /** R-99: a save carries the version the screen opened (If-Match); a save from an older version is refused. */
    @Test
    void aSaveFromAnOlderVersionDoesNotOverwriteSomeoneElsesChange() throws Exception {
        String month = (2400 + (int) (Long.parseLong(tag) % 500)) + "-06-01";
        Map<String, Object> body = deal(month);
        body.put("newClientName", "VERSION CLIENT " + tag);
        body.put("newClientPan", pan);
        JsonNode created = postJson("/api/bonds", body, admin, 201);
        long id = created.get("id").asLong();
        String opened = created.get("updatedAt").asText();

        Map<String, Object> first = deal(month);
        first.put("remarks", "first user");
        String saved = save(id, first, opened, 200).get("updatedAt").asText();
        Map<String, Object> second = deal(month);
        second.put("remarks", "second user, from the old screen");
        assertThat(save(id, second, opened, 409).get("detail").asText()).contains("Someone else saved this entry");
        assertThat(getJson("/api/bonds?from=" + month.substring(0, 7) + "&to=" + month.substring(0, 7), admin, 200).get(0).get("remarks").asText())
                .isEqualTo("first user");
        save(id, second, saved, 200);   // after opening it again
    }

    private JsonNode save(long id, Map<String, Object> body, String ifMatch, int expected) throws Exception {
        return body(mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/bonds/" + id), body)
                .header("If-Match", ifMatch).session(admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expected)).andReturn());
    }

    private void month(String first, String status) {
        java.time.LocalDate m = java.time.LocalDate.parse(first);
        jdbc.sql("""
                INSERT INTO period (product_type, month, from_date, to_date, status) VALUES ('MF', ?, ?, ?, ?)
                ON CONFLICT (product_type, month) DO UPDATE SET status = EXCLUDED.status""")
                .params(m, m, m.plusMonths(1).minusDays(1), status).update();
    }

    private String status(String first) {
        return jdbc.sql("SELECT status FROM period WHERE month = ?").param(java.time.LocalDate.parse(first)).query(String.class).single();
    }
}
