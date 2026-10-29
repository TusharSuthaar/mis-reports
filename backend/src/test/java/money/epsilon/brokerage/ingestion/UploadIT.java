package money.epsilon.brokerage.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;
import money.epsilon.brokerage.IntegrationTestBase;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** Finesse brokerage upload: codes → client master → PAN / guardian PAN → Master MIS client; the rest is "not in master". */
class UploadIT extends IntegrationTestBase {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;
    String tag;
    YearMonth month;
    String start, end;
    long rmA, rmB;
    long one, two;
    String c1a, c1b, c2, cMinor, cMinorOwnPan, cMissing;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        long t = Long.parseLong(tag);
        month = YearMonth.of(2100 + (int) (t % 800), 1 + (int) (t % 12)); // never shared between tests
        start = month.atDay(1).format(DAY);
        end = month.atEndOfMonth().format(DAY);
        long region = postJson("/api/regions", Map.of("name", "R-" + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "B-" + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rmA = postJson("/api/employees", Map.of("name", "RM A " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        rmB = postJson("/api/employees", Map.of("name", "RM B " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();

        String panOne = FinesseFiles.pan(t * 10 + 1), panTwo = FinesseFiles.pan(t * 10 + 2);
        String panMinor = FinesseFiles.pan(t * 10 + 3), panMissing = FinesseFiles.pan(t * 10 + 4);
        one = masterClient("ONE " + tag, panOne, rmA, "2026-01-01");
        two = masterClient("TWO " + tag, panTwo, rmB, "2026-01-01");
        c1a = "T" + tag + "A";
        c1b = "T" + tag + "B";
        c2 = "T" + tag + "C";
        cMinor = "T" + tag + "D";
        cMissing = "T" + tag + "E";
        cMinorOwnPan = "T" + tag + "F";
        uploadClientMaster(new FinesseFiles.ClientMaster()
                .client(c1a, "ONE " + tag, panOne, null)
                .client(c1b, "ONE JOINT " + tag, panOne, null)          // a second code of the same person
                .client(c2, "TWO " + tag, panTwo, null)
                .client(cMinor, "MINOR " + tag, null, panOne)           // a minor without a PAN: the guardian (ONE) counts
                .client(cMinorOwnPan, "MINOR2 " + tag, panMinor, panOne) // own PAN not in master: highlighted, guardian not used
                .client(cMissing, "MISSING " + tag, panMissing, null)   // PAN not in Master MIS
                .bytes(), admin);
    }

    /** ONE: 60 + 40 (two periods) + 20 (second code) + 10 (minor without PAN) = 130; TWO 50; MISSING 5.5555; MINOR2 7. */
    byte[] file(double extraOnTwo) {
        return new FinesseFiles.Brokerage()
                .row(c1a, "ONE " + tag, start, month.atDay(17).format(DAY), 60)
                .row(c1a, "ONE " + tag, month.atDay(18).format(DAY), end, 40)
                .row(c1b, "ONE JOINT " + tag, start, end, 20)
                .row(c2, "TWO " + tag, start, end, 50 + extraOnTwo)
                .row(cMinor, "MINOR " + tag, start, end, 10)
                .row(cMissing, "MISSING " + tag, start, end, 5.5555)
                .row(cMinorOwnPan, "MINOR2 " + tag, start, end, 7)
                .total(192.5555 + extraOnTwo)
                .bytes();
    }

    @Test
    void codesFindTheirClientByPanOrGuardianPanAndTheRestBlockProcess() throws Exception {
        long uploadId = stage(file(0));
        JsonNode review = getJson("/api/uploads/" + uploadId + "/review", admin, 200);
        assertThat(review.get("codes").asInt()).isEqualTo(6);
        assertThat(review.get("clients").asInt()).isEqualTo(4);
        assertThat(review.get("byPan").asInt()).isEqualTo(3);
        assertThat(review.get("byGuardianPan").asInt()).isEqualTo(1);
        assertThat(review.get("notInMaster")).hasSize(2);
        assertThat(review.get("notInMaster")).allSatisfy(n -> assertThat(n.get("reason").asText()).isEqualTo("PAN not in Master MIS"));
        assertThat(review.get("notInMaster")).extracting(n -> n.get("codes").asText()).containsExactly(cMinorOwnPan, cMissing); // biggest first
        assertThat(review.get("canSave").asBoolean()).isTrue();

        JsonNode saved = postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        assertThat(saved.get("lines").get("lines").asInt()).isEqualTo(4);
        assertThat(saved.get("lines").get("net").decimalValue()).isEqualByComparingTo("192.5555");
        assertThat(jdbc.sql("SELECT net FROM brokerage_line WHERE upload_id = ? AND client_id = ? AND NOT voided").params(uploadId, one)
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("130");

        JsonNode m = monthRow();
        assertThat(m.get("notInMaster").asInt()).isEqualTo(2);
        long periodId = m.get("periodId").asLong();
        JsonNode job = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("error").asText()).contains("2 client(s)").contains("not in Master MIS");

        // Give the highlighted clients an RM (as Master MIS would): the month can now be processed.
        for (JsonNode n : review.get("notInMaster")) {
            postJson("/api/clients/" + n.get("clientId").asLong() + "/rm", Map.of("rmId", rmB, "from", month.atDay(1).toString()), admin, 204);
        }
        assertThat(monthRow().get("notInMaster").asInt()).isZero();
        assertThat(runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong()).get("status").asText())
                .isEqualTo("DONE");
    }

    @Test
    void aClientWithoutPanIsMatchedByItsExactNameInMasterMis() throws Exception {
        long t = Long.parseLong(tag);
        long named = masterClient("NO PAN PERSON " + tag, null, rmA, "2026-01-01");     // a Master MIS row without a PAN
        String byName = "T" + tag + "G", wrongPan = "T" + tag + "H";
        uploadClientMaster(new FinesseFiles.ClientMaster()
                .client(byName, "No  Pan Person. " + tag, null, null)                      // no PAN; same name, other spacing
                .client(wrongPan, "TWO " + tag, FinesseFiles.pan(t * 10 + 8), null)        // own PAN not in master, name of TWO
                .bytes(), admin);
        long uploadId = stage(new FinesseFiles.Brokerage().row(byName, "x", start, end, 12).row(wrongPan, "x", start, end, 3).total(15).bytes());
        JsonNode review = getJson("/api/uploads/" + uploadId + "/review", admin, 200);
        assertThat(review.get("byName").asInt()).isEqualTo(1);
        assertThat(review.get("notInMaster")).singleElement()
                .satisfies(n -> assertThat(n.get("reason").asText()).contains("name matches a Master MIS client with another PAN"));
        postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        assertThat(jdbc.sql("SELECT net FROM brokerage_line WHERE upload_id = ? AND client_id = ? AND NOT voided").params(uploadId, named)
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("12");
    }

    @Test
    void aMinorWithItsOwnMasterMisRowIsBookedThereNotUnderTheGuardian() throws Exception {
        long minor = masterClient("OWN ROW MINOR " + tag, null, rmA, "2026-01-01");   // the minor's own Master MIS row, no PAN
        String code = "T" + tag + "M";
        uploadClientMaster(new FinesseFiles.ClientMaster().client(code, "OWN ROW MINOR " + tag, null, FinesseFiles.pan(Long.parseLong(tag) * 10 + 1)).bytes(), admin);   // guardian ONE
        long uploadId = stage(new FinesseFiles.Brokerage().row(code, "x", start, end, 7).total(7).bytes());
        JsonNode review = getJson("/api/uploads/" + uploadId + "/review", admin, 200);
        assertThat(review.get("byName").asInt()).isEqualTo(1);
        assertThat(review.get("byGuardianPan").asInt()).isZero();
        postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        assertThat(jdbc.sql("SELECT net FROM brokerage_line WHERE upload_id = ? AND client_id = ? AND NOT voided").params(uploadId, minor)
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("7");
    }

    @Test
    void aCodeWithNeitherPanNorGuardianPanIsMatchedByName() throws Exception {
        String code = "T" + tag + "N";
        uploadClientMaster(new FinesseFiles.ClientMaster().client(code, "Two  " + tag, null, null).bytes(), admin);   // TWO's name, no PANs
        long uploadId = stage(new FinesseFiles.Brokerage().row(code, "x", start, end, 9).total(9).bytes());
        assertThat(getJson("/api/uploads/" + uploadId + "/review", admin, 200).get("byName").asInt()).isEqualTo(1);
        postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        assertThat(jdbc.sql("SELECT net FROM brokerage_line WHERE upload_id = ? AND client_id = ? AND NOT voided").params(uploadId, two)
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("9");
    }

    @Test
    void theNotInMasterListDownloadsAsExcel() throws Exception {
        long uploadId = stage(file(0));
        byte[] xlsx = mvc.perform(get("/api/uploads/" + uploadId + "/not-in-master.xlsx").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var s = wb.getSheetAt(0);
            assertThat(s.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Why");
            assertThat(s.getLastRowNum()).isEqualTo(2);
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo(cMissing);
        }
    }

    @Test
    void theSameFileCannotBeUploadedTwiceButADiscardedOneCan() throws Exception {
        byte[] f = file(0);
        long uploadId = stage(f);
        mvc.perform(withCsrf(multipart("/api/uploads").file(new MockMultipartFile("file", "aug.xlsx", null, f)).param("month", month.toString()))
                .session(admin)).andExpect(status().isConflict());
        postJson("/api/uploads/" + uploadId + "/discard", Map.of(), admin, 204);
        stage(f);
    }

    @Test
    void aCorrectedFileReplacesTheMonthAndOldLinesAreVoidedNotDeleted() throws Exception {
        long first = stage(file(0));
        postJson("/api/uploads/" + first + "/commit", Map.of(), admin, 200);

        long second = stage(file(1));
        JsonNode saved = postJson("/api/uploads/" + second + "/commit", Map.of(), admin, 200);
        assertThat(saved.get("replacedUploadId").asLong()).isEqualTo(first);
        assertThat(saved.get("lines").get("net").decimalValue()).isEqualByComparingTo("193.5555");
        long voided = jdbc.sql("SELECT count(*) FROM brokerage_line WHERE upload_id = ? AND voided").param(first).query(Long.class).single();
        assertThat(voided).isEqualTo(4);
        assertThat(monthRow().get("saved").get("lines").asInt()).isEqualTo(4);
    }

    @Test
    void ledgerLinesCannotBeChangedOrDeleted() throws Exception {
        long uploadId = stage(file(0));
        postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        long line = jdbc.sql("SELECT id FROM brokerage_line WHERE upload_id = ? LIMIT 1").param(uploadId).query(Long.class).single();
        assertThatThrownBy(() -> jdbc.sql("UPDATE brokerage_line SET net = 1 WHERE id = ?").param(line).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM brokerage_line WHERE id = ?").param(line).update()).hasMessageContaining("append-only");
    }

    @Test
    void aFileThatDoesNotAddUpOrIsForAnotherMonthIsRejected() throws Exception {
        JsonNode bad = runUpload(new FinesseFiles.Brokerage().row(c2, "TWO", start, end, 100).total(1).bytes());
        assertThat(bad.get("uploadId").isNull()).isTrue();
        assertThat(bad.get("errors").get(0).get("message").asText()).contains("Total row says");

        YearMonth other = month.plusMonths(1);
        JsonNode wrongMonth = runUpload(new FinesseFiles.Brokerage()
                .row(c2, "TWO", other.atDay(1).format(DAY), other.atEndOfMonth().format(DAY), 100).total(100).bytes());
        assertThat(wrongMonth.get("errors").get(0).get("message").asText()).contains("Most of this file's brokerage is for " + other);
    }

    @Test
    void financeCanSeeMonthsButNotUpload() throws Exception {
        MockHttpSession finance = loginAs("FINANCE");
        getJson("/api/months", finance, 200);
        getJson("/api/finesse/client-master", finance, 200);
        mvc.perform(withCsrf(multipart("/api/uploads").file(new MockMultipartFile("file", "a.xlsx", null, file(0))).param("month", month.toString()))
                .session(finance)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(multipart("/api/finesse/client-master").file(new MockMultipartFile("file", "c.xlsx", null, new byte[]{1})))
                .session(finance)).andExpect(status().isForbidden());
    }

    @Test
    void theClientMasterRefusesAnotherFileAndReportsWhatChanged() throws Exception {
        mvc.perform(withCsrf(multipart("/api/finesse/client-master")
                        .file(new MockMultipartFile("file", "c.xlsx", null, new money.epsilon.brokerage.masterimport.TestWorkbook().sheet("X", "A").row("1").bytes())))
                .session(admin)).andExpect(status().isConflict());
        JsonNode again = uploadClientMaster(new FinesseFiles.ClientMaster()
                .client(c2, "TWO RENAMED " + tag, FinesseFiles.pan(Long.parseLong(tag) * 10 + 2), null).bytes(), admin);
        assertThat(again.get("updated").asInt()).isEqualTo(1);
        assertThat(again.get("added").asInt()).isZero();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    long stage(byte[] f) throws Exception {
        JsonNode result = runUpload(f);
        assertThat(result.get("errors")).as(result.toString()).isEmpty();
        return result.get("uploadId").asLong();
    }

    JsonNode runUpload(byte[] f) throws Exception {
        JsonNode accepted = body(mvc.perform(withCsrf(multipart("/api/uploads").file(new MockMultipartFile("file", "brokerage.xlsx", null, f))
                        .param("month", month.toString())).session(admin))
                .andExpect(status().isAccepted()).andReturn());
        JsonNode job = runJob(accepted.get("jobId").asLong());
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");
        return job.get("result");
    }

    JsonNode runJob(long jobId) throws Exception {
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            String s = getJson("/api/jobs/" + jobId, admin, 200).get("status").asText();
            return s.equals("DONE") || s.equals("FAILED");
        });
        return getJson("/api/jobs/" + jobId, admin, 200);
    }

    JsonNode monthRow() throws Exception {
        return StreamSupport.stream(getJson("/api/months", admin, 200).spliterator(), false)
                .filter(m -> m.get("month").asText().equals(month.toString())).findFirst().orElseThrow();
    }
}
