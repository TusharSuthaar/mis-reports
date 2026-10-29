package money.epsilon.brokerage.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** R-94, R-95: insurance policies typed on the screen or uploaded as the policy sheet, mapped to clients by PAN. */
class InsuranceIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "INR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "INB " + tag, "regionId", region, "from", "2023-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "INRM " + tag, "type", "RM", "branchId", branch, "from", "2023-01-01"), admin, 201).get("id").asLong();
    }

    private Map<String, Object> policy(String policyNo) {
        Map<String, Object> b = new HashMap<>();
        b.put("month", "2026-08-01");
        b.put("insurer", "INSURER" + tag);
        b.put("category", "HEALTH");
        b.put("productName", "OPTIMA" + tag);
        b.put("policyNo", policyNo);
        b.put("businessType", "NEW");
        b.put("totalPremium", 11800);
        b.put("netPremium", 10000);
        b.put("expectedPercent", 15);
        b.put("subType", "Floater");
        b.put("sumAssured", 500000);
        b.put("ppt", "1");
        return b;
    }

    @Test
    void aPolicyTakesItsClientsRmAndWorksOutTheCommissions() throws Exception {
        String pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 1);
        Map<String, Object> b = policy("P-" + tag);
        b.put("newClientName", "INS CLIENT " + tag);
        b.put("newClientPan", pan);
        postJson("/api/insurance", b, admin, 400);   // a new client needs its RM
        b.put("rmId", rm);
        b.put("newPospName", "POSP " + tag);
        b.put("pospShare", 50);
        JsonNode p = postJson("/api/insurance", b, admin, 201);
        assertThat(p.get("expectedComm").decimalValue()).isEqualByComparingTo("1500");   // 10,000 × 15%
        assertThat(p.get("pospPercent").decimalValue()).isEqualByComparingTo("7.5");    // 15% × 50%
        assertThat(p.get("pospComm").decimalValue()).isEqualByComparingTo("750");
        assertThat(p.get("rmName").asText()).isEqualTo("INRM " + tag);
        assertThat(p.get("region").asText()).isEqualTo("INR " + tag);
        long clientId = p.get("clientId").asLong();
        long pospId = p.get("pospId").asLong();

        // The next policy for the same client: found by its id, the RM is the client's; the POSP's agreed % is remembered.
        JsonNode d = getJson("/api/insurance/defaults?insurer=INSURER" + tag + "&productName=OPTIMA" + tag + "&pospId=" + pospId, admin, 200);
        assertThat(d.get("expectedPercent").decimalValue()).isEqualByComparingTo("15");
        assertThat(d.get("pospShare").decimalValue()).isEqualByComparingTo("50");
        Map<String, Object> b2 = policy("Q-" + tag);
        b2.put("clientId", clientId);
        b2.put("pospId", pospId);
        b2.put("pospShare", 50);
        b2.put("expectedComm", 1234);   // typed: stands instead of net × %
        JsonNode p2 = postJson("/api/insurance", b2, admin, 201);
        assertThat(p2.get("expectedComm").decimalValue()).isEqualByComparingTo("1234");
        assertThat(p2.get("expectedAsGiven").asBoolean()).isTrue();
        assertThat(p2.get("rmId").asLong()).isEqualTo(rm);

        b2.put("pospComm", 5000);
        // More than the expected commission is kept as given (the sheet has such policies); Epsilon's part goes negative.
        assertThat(putJson("/api/insurance/" + p2.get("id").asLong(), b2, admin, 200).get("pospComm").decimalValue()).isEqualByComparingTo("5000");
        assertThat(getJson("/api/insurance?q=" + tag, admin, 200)).hasSize(2);
        // Each category's own fields (R-96): Health needs its sum insured and cannot be FYCI.
        Map<String, Object> b3 = policy("R-" + tag);
        b3.put("clientId", clientId);
        b3.remove("sumAssured");
        assertThat(postJson("/api/insurance", b3, admin, 400).toString()).contains("sum insured");
        b3.put("sumAssured", 300000);
        b3.put("businessType", "FYCI");
        assertThat(postJson("/api/insurance", b3, admin, 400).toString()).contains("Health policies are");
        JsonNode cats = getJson("/api/insurance/categories", admin, 200);
        assertThat(cats).hasSize(4);
        assertThat(cats.get(2).get("category").asText()).isEqualTo("MOTOR");
        assertThat(cats.get(2).get("premium").asText()).isEqualTo("OD_TP");
        assertThat(getJson("/api/insurance/choices", admin, 200).get("insurers").toString()).contains("INSURER" + tag);
    }

    @Test
    void theMonthsPolicySheetIsMappedByPanAndRowsThatCannotBeMappedAreListed() throws Exception {
        String pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 2);
        Map<String, Object> c = new HashMap<>();
        c.put("name", "SHEET CLIENT " + tag);
        c.put("pan", pan);
        c.put("rmId", rm);
        c.put("from", "2026-01-01");
        postJson("/api/clients", c, admin, 201);
        String unknownPan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 3);
        byte[] sheet = sheet(List.of(
                new Object[]{"Aug'26", "S1-" + tag, "SHEET POSP " + tag, "INSURER " + tag, "Motor", "Name differs", pan, 1000, 900, "New", 0.2, 180, 0.5, 90},
                new Object[]{"Aug'26", "S2-" + tag, "NA", "INSURER " + tag, "Life", "SHEET CLIENT", pan, 5000, 5000, "Renewal", 0.1, 777, "-", 0},
                new Object[]{"Aug'26", "S3-" + tag, "NA", "INSURER " + tag, "Health", "No PAN", null, 2000, 2000, "New", 0.1, 200, "-", 0},
                new Object[]{"Aug'26", "S4-" + tag, "NA", "INSURER " + tag, "Health", "Unknown", unknownPan, 2000, 2000, "New", 0.1, 200, "-", 0},
                new Object[]{"Aug'26", "S6-" + tag, "NA", "INSURER " + tag, "General", "SHEET CLIENT " + tag, null, 1180, 1000, "New", 0.1, 100, "-", 0},  // no PAN: by name
                new Object[]{"Jul'26", "S5-" + tag, "NA", "INSURER " + tag, "Health", "Old month", pan, 2000, 2000, "New", 0.1, 200, "-", 0},
                new Object[]{null, null, null, null, null, null, null, 12000, 11900, null, null, 1377, null, 90}));   // the total row
        JsonNode up = upload(sheet);
        assertThat(up.get("rows").asInt()).isEqualTo(6);
        assertThat(up.get("saved").asInt()).isEqualTo(3);
        assertThat(up.get("newPosps").asInt()).isEqualTo(1);
        assertThat(up.get("expectedTotal").decimalValue()).isEqualByComparingTo("1057");   // 180 + 777 (typed, not 500) + 100 (no PAN, found by name)
        assertThat(up.get("pospTotal").decimalValue()).isEqualByComparingTo("90");
        assertThat(up.get("notSaved")).hasSize(3);
        assertThat(up.get("notSaved").toString()).contains("No PAN").contains("PAN not in Clients").contains("Business month is 2026-07");

        JsonNode list = getJson("/api/insurance?q=" + tag, admin, 200);
        assertThat(list).hasSize(3);
        assertThat(list.toString()).contains("SHEET CLIENT " + tag).contains("INRM " + tag);   // the client master's name and RM
        JsonNode motor = java.util.stream.StreamSupport.stream(list.spliterator(), false).filter(x -> x.get("category").asText().equals("MOTOR")).findFirst().orElseThrow();
        assertThat(motor.get("expectedPercent").decimalValue()).isEqualByComparingTo("20");
        assertThat(motor.get("pospShare").decimalValue()).isEqualByComparingTo("50");

        // Uploading again replaces the month's uploaded policies; nothing is counted twice.
        JsonNode again = upload(sheet);
        assertThat(again.get("replaced").asInt()).isEqualTo(3);
        assertThat(getJson("/api/insurance?q=" + tag, admin, 200)).hasSize(3);

        byte[] xlsx = mvc.perform(get("/api/insurance/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheet("Policy level data").getRow(0).getCell(22).getStringCellValue()).isEqualTo("Expect Comm.");
        }
    }

    private JsonNode upload(byte[] sheet) throws Exception {
        return body(mvc.perform(withCsrf(multipart("/api/insurance/upload").file(new MockMultipartFile("file", "ins.xlsx", null, sheet))
                        .param("month", "2026-08-01")).session(admin))
                .andExpect(status().isOk()).andReturn());
    }

    private static byte[] sheet(List<Object[]> rows) throws Exception {
        String[] h = {"Business Month", "Policy No", "POSP Name", "Insurance Company", "CAT", "Client", "PAN", "Total Premium", "Net Premuim",
                "Business Type", "% Expect Comm.", "Expect Comm.", "Agreed % sharing with POSP", "POSP Ideal Commission", "Product Name"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Summary");
            Sheet s = wb.createSheet("Policy level data");
            Row hr = s.createRow(0);
            for (int i = 0; i < h.length; i++) hr.createCell(i).setCellValue(h[i]);
            int r = 1;
            for (Object[] values : rows) {
                Row row = s.createRow(r++);
                for (int i = 0; i < values.length; i++) {
                    if (values[i] instanceof Number n) row.createCell(i).setCellValue(n.doubleValue());
                    else if (values[i] != null) row.createCell(i).setCellValue(values[i].toString());
                }
                if (values[1] != null) row.createCell(14).setCellValue("PRODUCT");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
