package money.epsilon.brokerage.equity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** R-89: the broker's Brokerage Due report is uploaded, kept trade by trade, and shown per client per month. */
class EquityIT extends IntegrationTestBase {

    MockHttpSession admin;
    long rm;
    String tag;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long region = postJson("/api/regions", Map.of("name", "EQR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "EQB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rm = postJson("/api/employees", Map.of("name", "EQRM " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
    }

    JsonNode upload(byte[] file, int expected) throws Exception {
        return body(mvc.perform(withCsrf(multipart("/api/equity/upload").file(new MockMultipartFile("file", "Equity Source.xlsx", null, file))).session(admin))
                .andExpect(status().is(expected)).andReturn());
    }

    @Test
    void aMonthsReportIsKeptPerTradeShownPerClientAndReplacedByALaterUpload() throws Exception {
        String pan = FinesseFiles.pan(Long.parseLong(tag) * 10 + 4);
        String unknown = FinesseFiles.pan(Long.parseLong(tag) * 10 + 5);
        postJson("/api/clients", Map.of("name", "EQ CLIENT " + tag, "pan", pan, "rmId", rm, "from", "2026-01-01"), admin, 201);
        int y = 2400 + (int) (Long.parseLong(tag) % 500);
        LocalDate d1 = LocalDate.of(y, 3, 3), d2 = LocalDate.of(y, 3, 17);
        byte[] march = new EquityFiles()
                .trade("P" + tag, pan, "EQ CLIENT", d1, "N", 100, 80)
                .trade("P" + tag, pan, "EQ CLIENT", d2, "B", 50, 40)
                .trade("U" + tag, unknown, "SOMEONE ELSE", d2, "N", 10, 6).bytes();
        JsonNode first = upload(march, 200);
        assertThat(first.get("month").asText()).isEqualTo(y + "-03");
        assertThat(first.get("trades").asInt()).isEqualTo(3);
        assertThat(first.get("clientsFound").asInt()).isEqualTo(1);
        assertThat(first.get("clientsNotFound").asInt()).isEqualTo(1);
        assertThat(first.get("shareTotal").decimalValue()).isEqualByComparingTo("126");

        JsonNode months = getJson("/api/equity?from=" + y + "-03&to=" + y + "-03", admin, 200);
        assertThat(months).hasSize(2);
        JsonNode mine = months.get(0);   // biggest share first
        assertThat(mine.get("share").decimalValue()).isEqualByComparingTo("120");
        assertThat(mine.get("gross").decimalValue()).isEqualByComparingTo("150");
        assertThat(mine.get("trades").asInt()).isEqualTo(2);
        assertThat(mine.get("rmName").asText()).isEqualTo("EQRM " + tag);
        assertThat(months.get(1).get("clientId").isNull()).isTrue();   // not a client yet
        assertThat(getJson("/api/equity/trades?month=" + y + "-03&party=P" + tag, admin, 200)).hasSize(2);

        // A later upload of the month replaces the earlier one.
        JsonNode again = upload(new EquityFiles().trade("P" + tag, pan, "EQ CLIENT", d1, "N", 200, 160).bytes(), 200);
        assertThat(again.get("replacedUploadId").asLong()).isEqualTo(first.get("uploadId").asLong());
        assertThat(getJson("/api/equity?from=" + y + "-03&to=" + y + "-03", admin, 200).get(0).get("share").decimalValue()).isEqualByComparingTo("160");
        assertThat(getJson("/api/equity/uploads", admin, 200).toString()).contains("REPLACED");

        // Trades of two months in one file are refused.
        upload(new EquityFiles().trade("P" + tag, pan, "EQ CLIENT", d1, "N", 1, 1).trade("P" + tag, pan, "EQ CLIENT", d1.plusMonths(1), "N", 1, 1).bytes(), 409);
    }
}
