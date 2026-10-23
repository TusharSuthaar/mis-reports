package money.epsilon.brokerage.preipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
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

/** R-92 and R-93: Available IPOs and Pre-IPO deals. */
class PreIpoIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long rm;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "PIR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "PIB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "PIRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
    }

    /** The register's price sheet: title lines, vendor names above each "Base Price (INR)" column, notes further down. */
    private byte[] priceSheet(String scrip, String isin, String vendorA, String vendorB) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Purchase Price Availabity");
            s.createRow(0).createCell(0).setCellValue("Dear All,");
            s.createRow(1).createCell(0).setCellValue("PRE IPO prices as on 24th September 2026 are mentioned below:");
            Row vendors = s.createRow(3);
            vendors.createCell(3).setCellValue("Unlisted PRE IPO Price List");
            vendors.createCell(6).setCellValue(vendorA);
            vendors.createCell(7).setCellValue(vendorB);
            String[] head = {"Sl No. ", "ISIN ", "Face Value", "Srcip Name", "Price", "Min. Lot size", "Base Price (INR)", "Base Price (INR)"};
            Row h = s.createRow(4);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            Row r = s.createRow(5);
            r.createCell(0).setCellValue(1);
            r.createCell(1).setCellValue(isin);
            r.createCell(2).setCellValue(10);
            r.createCell(3).setCellValue(scrip);
            r.createCell(4).setCellValue(104);
            r.createCell(5).setCellValue(200);
            r.createCell(6).setCellValue(100);
            r.createCell(7).setCellValue("-");   // this vendor has no price for it
            Row note = s.createRow(8);
            note.createCell(3).setCellValue("some scrip");
            note.createCell(4).setCellValue("good to invest");   // a note, not a price: skipped
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void thePriceSheetIsUploadedAndEachVendorsPriceIsKeptByDate() throws Exception {
        String scrip = "UNLISTED CO " + tag, isin = "INE0T" + String.format("%06d", Long.parseLong(tag) % 1000000) + "1";
        byte[] sheet = priceSheet(scrip, isin, "VENDOR A " + tag, "VENDOR B " + tag);
        JsonNode up = body(mvc.perform(withCsrf(multipart("/api/preipo/prices/upload").file(new MockMultipartFile("file", "prices.xlsx", null, sheet))
                        .param("asOf", "2026-09-24")).session(admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(up.get("newScrips").asInt()).isEqualTo(1);
        assertThat(up.get("prices").asInt()).isEqualTo(1);           // vendor B's "-" is not a price
        assertThat(up.get("newVendors").toString()).contains("VENDOR A " + tag).contains("VENDOR B " + tag);

        JsonNode row = findBoard(getJson("/api/preipo/prices?date=2026-09-30", admin, 200), scrip);
        assertThat(row.get("scrip").get("isin").asText()).isEqualTo(isin.toUpperCase());
        assertThat(row.get("scrip").get("lotSize").asInt()).isEqualTo(200);
        JsonNode price = row.get("prices").get(0);
        assertThat(price.get("basePrice").decimalValue()).isEqualByComparingTo("100");
        assertThat(price.get("clientPrice").decimalValue()).isEqualByComparingTo("104");   // base + 4% mark-up
        long scripId = row.get("scrip").get("id").asLong(), vendorId = price.get("vendorId").asLong();

        // A newer price does not replace the older one: the board on an earlier day still shows the older price.
        postJson("/api/preipo/prices", Map.of("scripId", scripId, "vendorId", vendorId, "asOf", "2026-10-05", "basePrice", 120, "markupPercent", 5), admin, 200);
        assertThat(findBoard(getJson("/api/preipo/prices?date=2026-10-10", admin, 200), scrip).get("prices").get(0).get("clientPrice").decimalValue())
                .isEqualByComparingTo("126");
        assertThat(findBoard(getJson("/api/preipo/prices?date=2026-09-30", admin, 200), scrip).get("prices").get(0).get("basePrice").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(getJson("/api/preipo/scrips/" + scripId + "/prices", admin, 200).size()).isEqualTo(2);
    }

    private static JsonNode findBoard(JsonNode board, String scrip) {
        for (JsonNode r : board) if (r.get("scrip").get("name").asText().equals(scrip)) return r;
        throw new AssertionError(scrip + " not on the board");
    }

    @Test
    void aBuyAndABuybackAreEnteredWithTheRegistersRevenue() throws Exception {
        long scrip = postJson("/api/preipo/scrips", Map.of("name", "DEAL CO " + tag, "isin", "INE9D" + tag.substring(0, 5), "lotSize", 100), admin, 201)
                .get("id").asLong();
        long vendor = postJson("/api/platforms", Map.of("name", "DEAL VENDOR " + tag), admin, 201).get("id").asLong();
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "BUY");
        body.put("newClientName", "PRE IPO CLIENT " + tag);
        body.put("newClientPan", FinesseFiles.pan(Long.parseLong(tag) * 10 + 7));
        body.put("month", "2026-09-01");
        body.put("rmId", rm);
        body.put("scripId", scrip);
        body.put("vendorId", vendor);
        body.put("dealDate", "2026-09-12");
        body.put("quantity", 100);
        body.put("clientPrice", 110);
        body.put("vendorPrice", 100);
        body.put("tdsApplied", true);
        body.put("tdsPercent", 5);
        body.put("dpName", "DEMO DP");
        JsonNode buy = postJson("/api/preipo", body, admin, 201);
        assertThat(buy.get("brokerage").decimalValue()).isEqualByComparingTo("1000");
        assertThat(buy.get("gross").decimalValue()).isEqualByComparingTo("847.46");
        assertThat(buy.get("net").decimalValue()).isEqualByComparingTo("950");
        assertThat(buy.get("gstPercent").decimalValue()).isEqualByComparingTo("18");   // from the Pre-IPO tax rate
        assertThat(buy.get("region").asText()).isEqualTo("PIR " + tag);
        assertThat(buy.get("billingStatus").asText()).isEqualTo("TO_CONFIRM");

        body.remove("newClientName");
        body.remove("newClientPan");
        body.put("clientId", buy.get("clientId").asLong());
        body.put("kind", "BUYBACK");
        body.put("clientPrice", 95);
        body.put("quantity", 50);
        JsonNode back = postJson("/api/preipo", body, admin, 201);
        assertThat(back.get("brokerage").decimalValue()).isEqualByComparingTo("250");
        // A buyback where the client would get more than the vendor pays is refused.
        body.put("clientPrice", 101);
        postJson("/api/preipo", body, admin, 409);

        assertThat(getJson("/api/preipo?q=" + tag + "&kind=BUYBACK", admin, 200).size()).isEqualTo(1);
        byte[] xlsx = mvc.perform(get("/api/preipo/export.xlsx?q=" + tag).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Pre IPO Purchase");
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(wb.getSheetAt(1).getRow(1).getCell(29).getStringCellValue()).isEqualTo("SELL");
        }
        postJson("/api/preipo/" + back.get("id").asLong() + "/cancel", Map.of("reason", "test"), admin, 204);
        assertThat(getJson("/api/preipo?q=" + tag, admin, 200).size()).isEqualTo(1);
    }
}
