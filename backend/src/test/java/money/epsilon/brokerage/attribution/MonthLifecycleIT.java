package money.epsilon.brokerage.attribution;

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
import java.util.Map;
import java.util.stream.StreamSupport;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.ingestion.FinesseFiles;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** Save a month → process (split) → lock → corrections; with partners, RM hierarchy and the locked-month guard. */
class MonthLifecycleIT extends IntegrationTestBase {

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;
    MockHttpSession finance;
    String tag;
    YearMonth month;
    String rmName, branchName, regionName, rdName;
    long rmId, sbId, refId;
    String code1, code2;
    long client1Id, client2Id;

    long branchId;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        finance = loginAs("FINANCE");
        tag = String.valueOf(System.nanoTime() % 1000000000L);
        month = YearMonth.of(2200 + (int) (Long.parseLong(tag) % 700), 1 + (int) (Long.parseLong(tag) % 11)); // never shared
        regionName = "REG " + tag;
        branchName = "BR " + tag;
        rmName = "RM " + tag;
        rdName = "RD " + tag;
        String start = month.minusYears(1) + "-01";
        long region = postJson("/api/regions", Map.of("name", regionName), admin, 201).get("id").asLong();
        branchId = postJson("/api/branches", Map.of("name", branchName, "regionId", region, "from", start), admin, 201).get("id").asLong();
        rmId = postJson("/api/employees", Map.of("name", rmName, "type", "RM", "branchId", branchId, "from", start), admin, 201).get("id").asLong();
        long rd = postJson("/api/employees", Map.of("name", rdName, "type", "RM", "branchId", branchId, "from", start), admin, 201).get("id").asLong();
        postJson("/api/regions/" + region + "/rd", Map.of("employeeId", rd, "from", start), admin, 204);
        sbId = postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", "SB " + tag), admin, 201).get("id").asLong();
        refId = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "REF " + tag), admin, 201).get("id").asLong();
        code1 = "L" + tag + "1";
        code2 = "L" + tag + "2";
        client1Id = masterClient("ONE " + tag, pan1(), rmId, start);
        client2Id = masterClient("TWO " + tag, pan2(), rmId, start);
        uploadClientMaster(new FinesseFiles.ClientMaster().client(code1, "ONE " + tag, pan1(), null).client(code2, "TWO " + tag, pan2(), null).bytes(), admin);
    }

    String pan1() {
        return FinesseFiles.pan(Long.parseLong(tag) * 10 + 1);
    }

    String pan2() {
        return FinesseFiles.pan(Long.parseLong(tag) * 10 + 2);
    }

    /** The test database is shared: a month left LOCKED would refuse every older dated change in other tests. */
    @AfterEach
    void unlockWhatThisTestLocked() throws Exception {
        for (JsonNode m : getJson("/api/months", admin, 200)) {
            if (m.get("status").asText().equals("LOCKED")) {
                postJson("/api/months/" + m.get("periodId").asLong() + "/unlock", Map.of("reason", "test clean-up"), admin, 200);
            }
        }
    }

    /** Finesse brokerage for the month: ONE {@code net1}, TWO 50 (amounts are net of GST). */
    byte[] file(double net1) {
        String from = month.atDay(1).format(DAY), to = month.atEndOfMonth().format(DAY);
        return new FinesseFiles.Brokerage()
                .row(code1, "ONE " + tag, from, to, net1)
                .row(code2, "TWO " + tag, from, to, 50)
                .total(net1 + 50)
                .bytes();
    }

    @Test
    void saveProcessLockAndCorrect() throws Exception {
        // August-like month: two clients, RM known. Save it.
        long periodId = saveMonth(file(100));
        long client1 = client1Id, client2 = client2Id;
        // client 1: sub-broker 50% + referral 20%; client 2: referral only 40%
        postJson("/api/clients/" + client1 + "/partner", Map.of("partnerId", sbId, "percent", 50, "from", month + "-01"), admin, 204);
        postJson("/api/clients/" + client1 + "/partner", Map.of("partnerId", refId, "percent", 20, "from", month + "-01"), admin, 204);
        postJson("/api/clients/" + client2 + "/partner", Map.of("partnerId", refId, "percent", 40, "from", month + "-01"), admin, 204);

        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("150");
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("80");   // 50 + 10 + 20
        assertThat(run.get("epsilon").decimalValue()).isEqualByComparingTo("70");    // 40 + 30
        assertThat(monthRow(periodId).get("status").asText()).isEqualTo("PROCESSED");

        JsonNode summary = getJson("/api/months/" + periodId + "/summary", admin, 200);
        assertThat(summary.get("subBrokers").decimalValue()).isEqualByComparingTo("50");
        assertThat(summary.get("referrals").decimalValue()).isEqualByComparingTo("30");
        assertThat(summary.get("byBranch").get(0).get("branch").asText()).isEqualTo(branchName);

        // The split remembers the hierarchy as it was.
        var snap = jdbc.sql("SELECT rm_at_period, branch_at_period, region_at_period, rd_at_period FROM brokerage_split WHERE period_id = ? AND NOT voided LIMIT 1")
                .param(periodId).query().singleRow();
        assertThat(snap).containsEntry("rm_at_period", rmName).containsEntry("branch_at_period", branchName)
                .containsEntry("region_at_period", regionName).containsEntry("rd_at_period", rdName);

        // Re-running voids the previous split; a dated change inside the month re-opens it.
        process(periodId);
        assertThat(jdbc.sql("SELECT count(*) FROM brokerage_split WHERE period_id = ? AND voided").param(periodId).query(Long.class).single())
                .isEqualTo(5);
        postJson("/api/clients/" + client2 + "/partner", Map.of("partnerId", refId, "percent", 45, "from", month + "-15"), admin, 204);
        assertThat(monthRow(periodId).get("status").asText()).isEqualTo("OPEN");
        postJson("/api/months/" + periodId + "/lock", Map.of(), finance, 409);
        process(periodId);

        // Only FINANCE locks.
        postJson("/api/months/" + periodId + "/lock", Map.of(), admin, 403);
        postJson("/api/months/" + periodId + "/lock", Map.of(), finance, 200);
        assertThat(monthRow(periodId).get("status").asText()).isEqualTo("LOCKED");

        // Locked: no change may reach into the month, and it cannot be processed again.
        postJson("/api/clients/" + client2 + "/partner", Map.of("partnerId", refId, "percent", 30, "from", month + "-20"), admin, 409);
        postJson("/api/clients/" + client1 + "/rm", Map.of("rmId", rmId, "from", month.minusMonths(2) + "-01"), admin, 409);
        postJson("/api/months/" + periodId + "/process", Map.of(), admin, 409);
        postJson("/api/clients/" + client2 + "/partner", Map.of("partnerId", refId, "percent", 30, "from", month.plusMonths(1) + "-01"), admin, 204);

        // A corrected file for the locked month: the difference goes to the next month as an adjustment.
        long correction = stage(file(110));
        JsonNode review = getJson("/api/uploads/" + correction + "/review", admin, 200);
        assertThat(review.get("correction").asBoolean()).isTrue();
        JsonNode posted = postJson("/api/uploads/" + correction + "/commit", Map.of(), admin, 200);
        assertThat(posted.get("adjustments").asInt()).isEqualTo(1);
        assertThat(posted.get("adjustmentsPostedTo").asText()).isEqualTo(month.plusMonths(1).toString());
        assertThat(posted.get("lines").get("net").decimalValue()).isEqualByComparingTo("10");
        assertThat(monthRow(periodId).get("saved").get("net").decimalValue()).as("locked month unchanged").isEqualByComparingTo("150");

        // The next month splits the adjustment as the locked month stood (client 1: 50% then 20%).
        long next = monthRow(month.plusMonths(1)).get("periodId").asLong();
        JsonNode nextRun = process(next);
        assertThat(nextRun.get("lines").asInt()).isEqualTo(1);
        assertThat(nextRun.get("partners").decimalValue()).isEqualByComparingTo("6");  // 5 + 1
        assertThat(jdbc.sql("SELECT bool_and(is_adjustment) FROM brokerage_split WHERE period_id = ? AND NOT voided").param(next)
                .query(Boolean.class).single()).isTrue();

        // Unlock: ADMIN only, with a reason.
        postJson("/api/months/" + periodId + "/unlock", Map.of("reason", "x"), finance, 403);
        postJson("/api/months/" + periodId + "/unlock", Map.of("reason", ""), admin, 400);
        postJson("/api/months/" + periodId + "/unlock", Map.of("reason", "Finance asked to fix a %"), admin, 200);
        assertThat(monthRow(periodId).get("status").asText()).isEqualTo("PROCESSED");
    }

    @Test
    void processingUsesTheRateCardUnlessTheClientHasItsOwnPercent() throws Exception {
        long periodId = saveMonth(file(100));   // client 1: net 100, client 2: net 50
        // Client 1 follows its sub-broker's rate card, which does not exist yet: Process stops and names the partner.
        postJson("/api/clients/" + client1Id + "/partner", Map.of("partnerId", sbId, "from", month + "-01"), admin, 204);
        JsonNode failed = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("error").asText()).contains("no MF %").contains("SB " + tag);

        postJson("/api/partners/" + sbId + "/products", Map.of("product", "MF", "percent", 50, "from", month + "-01"), admin, 204);
        postJson("/api/partners/" + refId + "/products", Map.of("product", "MF", "percent", 0, "from", month + "-01"), admin, 204);
        // Client 2: the referral partner's MF rate is 0%, but this client has its own 40%.
        postJson("/api/clients/" + client2Id + "/partner", Map.of("partnerId", refId, "percent", 40, "from", month + "-01"), admin, 204);
        assertThat(process(periodId).get("partners").decimalValue()).isEqualByComparingTo("70");   // 50 + 20

        // A partner whose approval is pending is paid nothing.
        patchJson("/api/partners/" + refId, Map.of("status", "PENDING"), admin, 200);
        assertThat(process(periodId).get("partners").decimalValue()).isEqualByComparingTo("50");
    }

    @Test
    void aBranchPartnerIsPaidFirstForEveryClientOfTheBranch() throws Exception {
        long periodId = saveMonth(file(100));   // client 1: net 100, client 2: net 50, both under an RM of this branch
        long branchSb = postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", "BRANCH SB " + tag), admin, 201).get("id").asLong();
        postJson("/api/partners/" + branchSb + "/products", Map.of("product", "MF", "percent", 55, "from", month + "-01"), admin, 204);
        postJson("/api/branches/" + branchId + "/partner", Map.of("partnerId", branchSb, "from", month + "-01"), admin, 204);
        // Client 1 also has a referral partner at 20%; client 2 its own sub-broker at 50%.
        postJson("/api/clients/" + client1Id + "/partner", Map.of("partnerId", refId, "percent", 20, "from", month + "-01"), admin, 204);
        postJson("/api/clients/" + client2Id + "/partner", Map.of("partnerId", sbId, "percent", 50, "from", month + "-01"), admin, 204);

        // Client 1: 55 + 20% of 45 = 9 → 64. Client 2: 27.50 + 50% of 22.50 = 11.25 → 38.75.
        assertThat(process(periodId).get("partners").decimalValue()).isEqualByComparingTo("102.75");
        postJson("/api/branches/" + branchId + "/partner/end", Map.of("lastDay", month.minusMonths(1).atEndOfMonth().toString()), admin, 204);
        assertThat(getJson("/api/branches/" + branchId + "/partner", admin, 200)).isEmpty();   // never took effect: removed
    }

    @Test
    void splitsCannotBeChangedOrDeleted() throws Exception {
        long periodId = saveMonth(file(100));
        process(periodId);
        long split = jdbc.sql("SELECT id FROM brokerage_split WHERE period_id = ? LIMIT 1").param(periodId).query(Long.class).single();
        assertThatThrownBy(() -> jdbc.sql("UPDATE brokerage_split SET amount = 1 WHERE id = ?").param(split).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM brokerage_split WHERE id = ?").param(split).update()).hasMessageContaining("append-only");
    }

    @Test
    void aMonthWithoutSavedFiguresCannotBeProcessed() throws Exception {
        long staged = stage(file(100));
        long periodId = getJson("/api/uploads/" + staged + "/review", admin, 200).get("upload").get("periodId").asLong();
        JsonNode job = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("error").asText()).contains("save");
    }

    @Test
    void endFileIsADraftUntilLockedAndMasksPanForHo() throws Exception {
        long periodId = saveMonth(file(100));
        long client1 = client1Id;
        postJson("/api/clients/" + client1 + "/partner", Map.of("partnerId", sbId, "percent", 50, "from", month + "-01"), admin, 204);
        mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isConflict());
        process(periodId);

        MockHttpSession ho = loginAs("HO");
        MockHttpServletResponse draft = mvc.perform(get("/api/months/" + periodId + "/end-file").session(ho)).andExpect(status().isOk()).andReturn().getResponse();
        String mon = month.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
        assertThat(draft.getHeader("Content-Disposition")).contains("DRAFT_END_File_" + mon + "_" + month.getYear() + ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(draft.getContentAsByteArray()))) {
            String sheet = "Data_" + mon + "'" + String.valueOf(month.getYear()).substring(2);
            var data = wb.getSheet(sheet);
            assertThat(data).as(sheet).isNotNull();
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(8) != null && ("ONE " + tag).equals(r.getCell(8).getStringCellValue())).findFirst().orElseThrow();
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(regionName);
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo(branchName);
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo(rmName);
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo(pan1().substring(0, 5) + "****" + pan1().substring(9));
            assertThat(StreamSupport.stream(wb.getSheet("Summary").spliterator(), false)
                    .anyMatch(r -> r.getCell(12) != null && r.getCell(12).getStringCellValue().startsWith("DRAFT"))).isTrue();
        }

        postJson("/api/months/" + periodId + "/lock", Map.of(), finance, 200);
        MockHttpServletResponse fin = mvc.perform(get("/api/months/" + periodId + "/end-file").session(finance)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(fin.getHeader("Content-Disposition")).doesNotContain("DRAFT");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(fin.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            assertThat(StreamSupport.stream(data.spliterator(), false)
                    .anyMatch(r -> r.getCell(9) != null && pan1().equals(r.getCell(9).getStringCellValue()))).as("full PAN for FINANCE").isTrue();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM audit_log WHERE entity = 'END_FILE' AND entity_id = ? AND action = 'EXPORT'")
                .param(String.valueOf(periodId)).query(Long.class).single()).isEqualTo(2);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    long saveMonth(byte[] f) throws Exception {
        long uploadId = stage(f);
        postJson("/api/uploads/" + uploadId + "/commit", Map.of(), admin, 200);
        return getJson("/api/uploads/" + uploadId + "/review", admin, 200).get("upload").get("periodId").asLong();
    }

    long stage(byte[] f) throws Exception {
        JsonNode accepted = body(mvc.perform(withCsrf(multipart("/api/uploads").file(new MockMultipartFile("file", "f.xlsx", null, f))
                .param("month", month.toString())).session(admin)).andExpect(status().isAccepted()).andReturn());
        JsonNode job = runJob(accepted.get("jobId").asLong());
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");
        assertThat(job.get("result").get("errors")).as(job.toString()).isEmpty();
        return job.get("result").get("uploadId").asLong();
    }

    JsonNode process(long periodId) throws Exception {
        JsonNode job = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
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

    JsonNode monthRow(long periodId) throws Exception {
        return StreamSupport.stream(getJson("/api/months", admin, 200).spliterator(), false)
                .filter(m -> m.get("periodId").asLong() == periodId).findFirst().orElseThrow();
    }

    JsonNode monthRow(YearMonth m) throws Exception {
        return StreamSupport.stream(getJson("/api/months", admin, 200).spliterator(), false)
                .filter(x -> x.get("month").asText().equals(m.toString())).findFirst().orElseThrow();
    }
}
