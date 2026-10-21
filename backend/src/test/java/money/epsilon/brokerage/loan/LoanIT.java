package money.epsilon.brokerage.loan;

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

/** R-80: the Loans screen. */
class LoanIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    String pan;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 6);
        long region = postJson("/api/regions", Map.of("name", "LNR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "LNB " + tag, "regionId", region, "from", "2024-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "LNRM " + tag, "type", "RM", "branchId", branch, "from", "2024-01-01"), admin, 201).get("id").asLong();
    }

    private Map<String, Object> entry(String month) {
        Map<String, Object> m = new HashMap<>();
        m.put("month", month);
        m.put("rmId", rm);
        m.put("lender", "TATA");
        m.put("loanType", "Personal Loan");
        m.put("disbursalDate", month);
        m.put("sanctionedAmount", 360661);
        m.put("ratePercent", 15.25);
        m.put("payoutPercent", 1.5);
        m.put("tdsApplied", true);
        m.put("tdsPercent", 2);
        return m;
    }

    @Test
    void aLoanPayoutIsEnteredForANewClientWithItsRevenueAndTheRegistersColumns() throws Exception {
        Map<String, Object> body = entry("2026-07-01");
        body.put("newClientName", "LOAN CLIENT " + tag);
        body.put("newClientPan", pan);
        body.put("paymentStatus", "RECEIVED");
        body.put("receivedOn", "2026-08-24");
        body.put("paymentNote", "19-08-2026");
        JsonNode e = postJson("/api/loans", body, admin, 201);
        assertThat(e.get("gross").decimalValue()).isEqualByComparingTo("5409.92");
        assertThat(e.get("tds").decimalValue()).isEqualByComparingTo("108.20");
        assertThat(e.get("gst").decimalValue()).isEqualByComparingTo("973.79");
        assertThat(e.get("region").asText()).isEqualTo("LNR " + tag);
        long id = e.get("id").asLong();

        // An LAS trail payout: 0.25% of the utilised amount, its own entry in its own month.
        Map<String, Object> trail = entry("2026-09-01");
        trail.put("clientId", e.get("clientId").asLong());
        trail.put("loanType", "LAS");
        trail.put("sanctionedAmount", 1798000);
        trail.put("utilisedAmount", 1796000);
        trail.put("payoutKind", "TRAIL");
        trail.put("payoutOn", "UTILISED");
        trail.put("payoutPercent", 0.25);
        assertThat(postJson("/api/loans", trail, admin, 201).get("gross").decimalValue()).isEqualByComparingTo("4490");
        trail.remove("utilisedAmount");
        postJson("/api/loans", trail, admin, 400);   // the payout is on the utilised amount: it is needed

        // All months by default, filtered by lender / text; suggestions remember the payout %.
        assertThat(getJson("/api/loans?q=" + tag, admin, 200)).hasSize(2);
        assertThat(getJson("/api/loans?q=" + tag + "&from=2026-09", admin, 200)).hasSize(1);
        assertThat(getJson("/api/loans/suggestions", admin, 200).get("lenders").toString()).contains("TATA");

        // The register's own figures can be kept as they are (R-79).
        Map<String, Object> given = entry("2024-01-01");
        given.put("clientId", e.get("clientId").asLong());
        given.put("loanType", "Home Loan");
        given.put("sanctionedAmount", 4500000);
        given.remove("payoutPercent");
        given.put("figuresAsGiven", true);
        given.put("givenGross", 13500);
        given.put("givenTds", 675);
        given.put("givenGst", 2430);
        JsonNode g = postJson("/api/loans", given, admin, 201);
        assertThat(g.get("net").decimalValue()).isEqualByComparingTo("12825");
        assertThat(g.get("invoice").decimalValue()).isEqualByComparingTo("15930");

        // A closed loan goes to the Closed section of the Excel, after the open ones.
        Map<String, Object> closed = entry("2024-04-01");
        closed.put("clientId", e.get("clientId").asLong());
        closed.put("loanType", "LAS");
        closed.put("loanClosed", true);
        closed.put("payoutPercent", 0);
        assertThat(postJson("/api/loans", closed, admin, 201).get("loanClosed").asBoolean()).isTrue();

        // Excel in the register's column order.
        byte[] xlsx = mvc.perform(get("/api/loans/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheet("LOANS");
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("PARTNER");
            assertThat(sheet.getRow(0).getCell(17).getStringCellValue()).isEqualTo("Without GST");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Closed");   // 3 open loans (rows 1-3), a gap, then Closed
            assertThat(sheet.getRow(6).getCell(11).getStringCellValue()).isEqualTo("LAS");
            assertThat(sheet.getRow(6).getCell(22).getStringCellValue()).isEqualTo("Loan Closed");
        }

        postJson("/api/loans/" + id + "/cancel", Map.of("reason", "test"), admin, 204);
        assertThat(getJson("/api/loans?q=" + tag, admin, 200)).hasSize(3);
        // Loans TDS: 5% until 30 Sep 2024, 2% from 1 Oct 2024.
        assertThat(getJson("/api/product-taxes/LOAN/on?date=2024-09-30", admin, 200).get("tdsPercent").decimalValue()).isEqualByComparingTo("5");
        assertThat(getJson("/api/product-taxes/LOAN/on?date=2024-10-01", admin, 200).get("tdsPercent").decimalValue()).isEqualByComparingTo("2");
    }
}
