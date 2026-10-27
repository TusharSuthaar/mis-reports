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

        // The MF tab lists a saved month before it is processed: figures, no split yet (R-81).
        JsonNode saved = getJson("/api/mf?q=" + tag + "&from=" + month + "&to=" + month, admin, 200);
        assertThat(saved.get("rows")).hasSize(2);
        assertThat(saved.get("totals").get("net").decimalValue()).isEqualByComparingTo("150");
        assertThat(saved.get("rows").get(0).get("epsilon").isNull()).isTrue();

        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("150");
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("80");   // 50 + 10 + 20

        // ... and once processed, with partners and what Epsilon keeps; filters by RM and partner.
        JsonNode mfTab = getJson("/api/mf?q=" + tag + "&from=" + month + "&to=" + month, admin, 200);
        assertThat(mfTab.get("totals").get("partners").decimalValue()).isEqualByComparingTo("80");
        assertThat(mfTab.get("totals").get("epsilon").decimalValue()).isEqualByComparingTo("70");
        assertThat(getJson("/api/mf?q=" + tag + "&partnerId=" + sbId, admin, 200).get("rows")).hasSize(1);
        assertThat(getJson("/api/mf?q=" + tag + "&rmId=" + rmId, admin, 200).get("rows")).hasSize(2);
        assertThat(run.get("epsilon").decimalValue()).isEqualByComparingTo("70");    // 40 + 30
        assertThat(monthRow(periodId).get("status").asText()).isEqualTo("PROCESSED");

        // The client's page shows the month's MF brokerage and how it was shared (R-09).
        JsonNode mf = getJson("/api/clients/" + client1 + "/brokerage?product=MF", admin, 200);
        assertThat(mf).hasSize(1);
        assertThat(mf.get(0).get("month").asText()).isEqualTo(month.toString());
        assertThat(mf.get(0).get("net").decimalValue()).isEqualByComparingTo("100");
        assertThat(mf.get(0).get("partnersTotal").decimalValue()).isEqualByComparingTo("60");   // sub-broker 50 + referral 10
        assertThat(mf.get(0).get("epsilon").decimalValue()).isEqualByComparingTo("40");
        assertThat(mf.get(0).get("partners").toString()).contains("SB " + tag).contains("REF " + tag);
        assertThat(mf.get(0).get("rm").asText()).isEqualTo(rmName);

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
        assertThat(getJson("/api/months/" + periodId + "/readiness", admin, 200).get("missingPercent").toString()).contains("SB " + tag + " (MF)");

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

    @Test
    void theMonthsFdEntriesAreSplitWithTheirPartnersFdPercentAndShownInTheEndFile() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        Map<String, Object> fd = new java.util.HashMap<>();
        fd.put("clientId", client1Id);
        fd.put("month", month + "-01");
        fd.put("rmId", rmId);
        fd.put("partnerId", refId);
        fd.put("fdName", "FD CO " + tag);
        fd.put("fdNo", "N" + tag);
        fd.put("interestFrequency", "CUMULATIVE");
        fd.put("depositDate", month + "-01");
        fd.put("periodMonths", 12);
        fd.put("maturityDate", month.plusMonths(12) + "-01");
        fd.put("ratePercent", 8);
        fd.put("amount", 100000);
        fd.put("certificate", "RECEIVED");
        fd.put("marginPercent", 1);   // commission 1,000 excl. GST
        postJson("/api/fd", fd, admin, 201);

        // The pre-check (R-91) names the gap before anyone presses Process, and changes nothing.
        JsonNode check = getJson("/api/months/" + periodId + "/readiness", admin, 200);
        assertThat(check.get("ready").asBoolean()).isFalse();
        assertThat(check.get("entries").get("FD").asInt()).isEqualTo(1);
        assertThat(check.get("missingPercent").toString()).contains("REF " + tag + " (FD)");
        assertThat(jdbc.sql("SELECT count(*) FROM brokerage_line WHERE period_id = ? AND product_type = 'FD'").param(periodId)
                .query(Long.class).single()).isZero();

        // The referral partner chosen on the FD has no FD % yet: Process stops and names it.
        JsonNode failed = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("error").asText()).contains("no FD %").contains("REF " + tag);

        postJson("/api/partners/" + refId + "/products", Map.of("product", "FD", "percent", 50, "from", month + "-01"), admin, 204);
        assertThat(getJson("/api/months/" + periodId + "/readiness", admin, 200).get("ready").asBoolean()).isTrue();
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("1150");      // MF 150 + FD 1,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("500");   // 50% of the FD commission
        // Re-running keeps one FD line (the earlier one is voided, never changed).
        process(periodId);
        assertThat(jdbc.sql("SELECT count(*) FROM brokerage_line WHERE period_id = ? AND product_type = 'FD' AND NOT voided").param(periodId)
                .query(Long.class).single()).isEqualTo(1);

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith("FD: FD CO " + tag)).findFirst().orElseThrow();
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("ONE " + tag);
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(100000.0);   // AUM / amount invested
            assertThat(row.getCell(15).getNumericCellValue()).isEqualTo(1000.0);     // FD column
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(1000.0);     // Total Rev
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(500.0);      // Net Revenue
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("REF " + tag);
        }
    }

    @Test
    void theMonthsEquityBrokerageIsSplitAtThePartnersEquityPercentAndShownInTheEquityColumn() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50
        // Client 1: sub-broker at 50% for this client, no Equity % on its rate card: equity follows the client's 50%.
        postJson("/api/clients/" + client1Id + "/partner", Map.of("partnerId", sbId, "percent", 50, "from", month + "-01"), admin, 204);
        // Client 2: referral at 0% for MF, but the referral's rate card has Equity 40%: equity is still shared at 40%.
        postJson("/api/clients/" + client2Id + "/partner", Map.of("partnerId", refId, "percent", 0, "from", month + "-01"), admin, 204);
        postJson("/api/partners/" + refId + "/products", Map.of("product", "EQUITY", "percent", 40, "from", month + "-01"), admin, 204);
        byte[] report = new money.epsilon.brokerage.equity.EquityFiles()
                .trade("P" + tag, pan1(), "ONE " + tag, month.atDay(5), "N", 250, 200)
                .trade("P" + tag, pan1(), "ONE " + tag, month.atDay(20), "B", 125, 100)
                .trade("Q" + tag, pan2(), "TWO " + tag, month.atDay(7), "N", 200, 100).bytes();
        mvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/equity/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "Equity Source.xlsx", null, report))).session(admin))
                .andExpect(status().isOk());

        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("550");      // MF 150 + equity 300 + 100
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("240");  // 50% of client 1's MF 100 and equity 300; 40% of client 2's equity 100

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(19) != null && r.getCell(19).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                            && r.getCell(19).getNumericCellValue() == 300.0).findFirst().orElseThrow();
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("ONE " + tag);
            assertThat(row.getCell(13) == null || row.getCell(13).getCellType() != org.apache.poi.ss.usermodel.CellType.NUMERIC
                    || row.getCell(13).getNumericCellValue() == 0).as("no MF on the equity row").isTrue();
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(300.0);   // Total Rev
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(150.0);   // Net Revenue after 50% sharing
        }
    }

    @Test
    void theMonthsGiftCityBrokerageIsSplitAndShownInItsOwnEndColumn() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        Map<String, Object> gift = new java.util.HashMap<>();
        gift.put("clientId", client1Id);
        gift.put("month", month + "-01");
        gift.put("rmId", rmId);
        gift.put("partnerId", refId);
        gift.put("fund", "GIFT FUND " + tag);
        gift.put("amountUsd", 10000);
        gift.put("fxRate", 90);
        gift.put("brokerage", 3000);
        postJson("/api/gift", gift, admin, 201);
        // An entry without brokerage yet is not a revenue line.
        gift.put("brokerage", null);
        gift.put("fund", "NO BROKERAGE " + tag);
        postJson("/api/gift", gift, admin, 201);

        postJson("/api/partners/" + refId + "/products", Map.of("product", "GIFT", "percent", 10, "from", month + "-01"), admin, 204);
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("3150");     // MF 150 + GIFT City 3,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("300");  // 10% of the GIFT City brokerage

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            assertThat(data.getRow(0).getCell(22).getStringCellValue()).isEqualTo("GIFT CITY");
            assertThat(data.getRow(0).getCell(25).getStringCellValue()).isEqualTo("Total Rev");
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith("GIFT City: GIFT FUND " + tag)).findFirst().orElseThrow();
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(900000.0);   // AUM: USD 10,000 × 90
            assertThat(row.getCell(22).getNumericCellValue()).isEqualTo(3000.0);     // GIFT CITY column
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(3000.0);     // Total Rev
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(2700.0);     // Net Revenue
        }
    }

    @Test
    void theMonthsPreIpoDealsAreSplitAndShownInThePreIpoColumn() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        long scrip = postJson("/api/preipo/scrips", Map.of("name", "END CO " + tag), admin, 201).get("id").asLong();
        long vendor = postJson("/api/platforms", Map.of("name", "END VENDOR " + tag), admin, 201).get("id").asLong();
        Map<String, Object> deal = new java.util.HashMap<>();
        deal.put("kind", "BUY");
        deal.put("clientId", client1Id);
        deal.put("month", month + "-01");
        deal.put("rmId", rmId);
        deal.put("partnerId", refId);
        deal.put("scripId", scrip);
        deal.put("vendorId", vendor);
        deal.put("dealDate", month + "-10");
        deal.put("quantity", 100);
        deal.put("clientPrice", 110);
        deal.put("vendorPrice", 100);   // brokerage 1,000 incl. GST → 847.46 without GST
        postJson("/api/preipo", deal, admin, 201);

        // The referral partner has no Pre-IPO % yet: the pre-check names it.
        assertThat(getJson("/api/months/" + periodId + "/readiness", admin, 200).get("missingPercent").toString()).contains("REF " + tag + " (Pre-IPO)");
        postJson("/api/partners/" + refId + "/products", Map.of("product", "PREIPO", "percent", 20, "from", month + "-01"), admin, 204);
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("997.46");     // MF 150 + Pre-IPO 847.46
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("169.49"); // 20% of the Pre-IPO brokerage

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            assertThat(data.getRow(0).getCell(14).getStringCellValue()).isEqualTo("Pre-IPO");
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith("Pre-IPO buy: END CO " + tag)).findFirst().orElseThrow();
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(11000.0);    // AUM: 100 × 110
            assertThat(row.getCell(14).getNumericCellValue()).isEqualTo(847.46);     // Pre-IPO column
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(847.46);     // Total Rev
        }
    }

    @Test
    void theMonthsPmsNpsAndP2pRevenueIsSplitAndShownInTheirOwnEndColumns() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        Map<String, Object> pms = new java.util.HashMap<>();
        pms.put("clientId", client1Id);
        pms.put("month", month.minusMonths(2) + "-01");
        pms.put("rmId", rmId);
        pms.put("partnerId", refId);
        pms.put("amc", "PMS AMC " + tag);
        pms.put("strategy", "MULTICAP");
        pms.put("amount", 5000000);
        pms.put("investmentDate", month.minusMonths(2) + "-10");
        long investment = postJson("/api/pms", pms, admin, 201).get("id").asLong();
        // The AMC's payout received this month: this client's share as typed.
        postJson("/api/pms/fees", Map.of("investmentId", investment, "month", month + "-01", "amount", 3000), admin, 201);

        Map<String, Object> nps = new java.util.HashMap<>();
        nps.put("clientId", client1Id);
        nps.put("month", month + "-01");
        nps.put("rmId", rmId);
        nps.put("partnerId", refId);
        nps.put("pop", "NPS POP " + tag);
        nps.put("investmentDate", month + "-05");
        nps.put("amount", 200000);
        nps.put("revenue", 1000);
        postJson("/api/nps", nps, admin, 201);

        Map<String, Object> p2p = new java.util.HashMap<>();
        p2p.put("clientId", client1Id);
        p2p.put("month", month + "-01");
        p2p.put("rmId", rmId);
        p2p.put("partnerId", refId);
        p2p.put("platform", "P2P PLATFORM " + tag);
        p2p.put("scheme", "YEARLY");
        p2p.put("roiPercent", 12);
        p2p.put("amount", 300000);
        p2p.put("brokerage", 2000);
        postJson("/api/p2p", p2p, admin, 201);

        assertThat(getJson("/api/months/" + periodId + "/readiness", admin, 200).get("missingPercent").toString())
                .contains("REF " + tag + " (PMS)").contains("REF " + tag + " (NPS)").contains("REF " + tag + " (P2P)");
        postJson("/api/partners/" + refId + "/products", Map.of("product", "PMS", "percent", 75, "from", month.minusMonths(2) + "-01"), admin, 204);
        postJson("/api/partners/" + refId + "/products", Map.of("product", "NPS", "percent", 10, "from", month + "-01"), admin, 204);
        postJson("/api/partners/" + refId + "/products", Map.of("product", "P2P", "percent", 20, "from", month + "-01"), admin, 204);
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("6150");      // MF 150 + PMS 3,000 + NPS 1,000 + P2P 2,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("2750");  // 75% of 3,000 + 10% of 1,000 + 20% of 2,000

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            assertThat(data.getRow(0).getCell(20).getStringCellValue()).isEqualTo("PMS");
            assertThat(data.getRow(0).getCell(23).getStringCellValue()).isEqualTo("NPS");
            assertThat(data.getRow(0).getCell(24).getStringCellValue()).isEqualTo("P2P");
            assertThat(data.getRow(0).getCell(25).getStringCellValue()).isEqualTo("Total Rev");
            java.util.function.Function<String, org.apache.poi.ss.usermodel.Row> byRemark = prefix -> StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith(prefix)).findFirst().orElseThrow();
            var pmsRow = byRemark.apply("PMS: PMS AMC " + tag);
            assertThat(pmsRow.getCell(20).getNumericCellValue()).isEqualTo(3000.0);
            assertThat(pmsRow.getCell(29).getNumericCellValue()).isEqualTo(750.0);     // Net Revenue after the partner's 75%
            var npsRow = byRemark.apply("NPS: NPS POP " + tag);
            assertThat(npsRow.getCell(23).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(npsRow.getCell(10).getNumericCellValue()).isEqualTo(200000.0);  // AUM: amount invested
            var p2pRow = byRemark.apply("P2P: P2P PLATFORM " + tag);
            assertThat(p2pRow.getCell(24).getNumericCellValue()).isEqualTo(2000.0);
            assertThat(p2pRow.getCell(25).getNumericCellValue()).isEqualTo(2000.0);   // Total Rev
        }
    }

    @Test
    void theMonthsInsurancePoliciesShowTheExpectedCommissionAndPayThePospOnly() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        Map<String, Object> policy = new java.util.HashMap<>();
        policy.put("clientId", client1Id);
        policy.put("month", month + "-01");
        policy.put("insurer", "INSURER " + tag);
        policy.put("category", "LIFE");
        policy.put("productName", "TERM PLAN");
        policy.put("subType", "Term");
        policy.put("sumAssured", 10000000);
        policy.put("ppt", "10");
        policy.put("policyTerm", 30);
        policy.put("policyNo", "POL-" + tag);
        policy.put("businessType", "NEW");
        policy.put("totalPremium", 10000);
        policy.put("netPremium", 10000);
        policy.put("expectedPercent", 15);
        policy.put("newPospName", "POSP " + tag);
        policy.put("pospShare", 50);
        postJson("/api/insurance", policy, admin, 201);
        assertThat(getJson("/api/months/" + periodId + "/readiness", admin, 200).get("entries").get("INS").asInt()).isEqualTo(1);

        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("1650");      // MF 150 + expected commission 1,500
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("750");  // the POSP's 15% × 50% of 10,000

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            assertThat(data.getRow(0).getCell(18).getStringCellValue()).isEqualTo("Insurance");
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().equals("Insurance: INSURER " + tag + " · TERM PLAN"))
                    .findFirst().orElseThrow();
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("POSP " + tag);
            assertThat(row.getCell(18).getNumericCellValue()).isEqualTo(1500.0);   // Insurance column
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(1500.0);   // Total Rev
            assertThat(row.getCell(26).getNumericCellValue()).isEqualTo(0.0);      // no Ref Sharing on insurance
            assertThat(row.getCell(27).getNumericCellValue()).isEqualTo(750.0);    // POSP Sharing
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(750.0);    // Net Revenue
        }
    }

    @Test
    void theMonthsLoanPayoutsAreSplitWithTheirPartnersLoansPercentAndShownInTheLoansColumn() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50, no partners
        Map<String, Object> loan = new java.util.HashMap<>();
        loan.put("clientId", client1Id);
        loan.put("month", month + "-01");
        loan.put("rmId", rmId);
        loan.put("partnerId", refId);
        loan.put("lender", "LENDER " + tag);
        loan.put("loanType", "Home Loan");
        loan.put("disbursalDate", month + "-01");
        loan.put("sanctionedAmount", 200000);
        loan.put("payoutPercent", 1);   // payout 2,000 excl. GST
        loan.put("tdsApplied", true);
        loan.put("tdsPercent", 2);
        postJson("/api/loans", loan, admin, 201);

        JsonNode failed = runJob(postJson("/api/months/" + periodId + "/process", Map.of(), admin, 202).get("jobId").asLong());
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("error").asText()).contains("REF " + tag + " (Loans)");

        postJson("/api/partners/" + refId + "/products", Map.of("product", "LOAN", "percent", 25, "from", month + "-01"), admin, 204);
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("2150");      // MF 150 + loan 2,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("500");   // 25% of the loan payout

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var data = wb.getSheetAt(2);
            var row = StreamSupport.stream(data.spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith("Loan: LENDER " + tag)).findFirst().orElseThrow();
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(200000.0);   // AUM / sanctioned amount
            assertThat(row.getCell(17).getNumericCellValue()).isEqualTo(2000.0);     // Loans column
            assertThat(row.getCell(25).getNumericCellValue()).isEqualTo(2000.0);     // Total Rev
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(1500.0);     // Net Revenue
        }
    }

    @Test
    void theMonthsBondDealsAreSplitWithTheirPartnersBondsPercentAndShownInTheEndFile() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50
        long platform = postJson("/api/platforms", Map.of("name", "PF " + tag), admin, 201).get("id").asLong();
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", false, "tdsPercent", 5, "ourShare", 100, "from", month + "-01"), admin, 204);
        Map<String, Object> bond = new java.util.HashMap<>();
        bond.put("clientId", client2Id);
        bond.put("month", month + "-01");
        bond.put("rmId", rmId);
        bond.put("partnerId", sbId);
        bond.put("platformId", platform);
        bond.put("bondName", "BOND CO " + tag);
        bond.put("isin", "ISIN" + tag);
        bond.put("investmentDate", month + "-05");
        bond.put("quantity", 10);
        bond.put("investmentAmount", 400000);
        bond.put("accruedInterest", 1000);
        bond.put("markupPercent", 0.5);   // gross 2,000 (GST on top)
        postJson("/api/bonds", bond, admin, 201);

        postJson("/api/partners/" + sbId + "/products", Map.of("product", "BOND", "percent", 40, "from", month + "-01"), admin, 204);
        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("2150");     // MF 150 + bond 2,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("800");  // 40% of the bond's gross

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var row = StreamSupport.stream(wb.getSheetAt(2).spliterator(), false)
                    .filter(r -> r.getCell(31) != null && r.getCell(31).getStringCellValue().startsWith("Bond: BOND CO " + tag)).findFirst().orElseThrow();
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("TWO " + tag);
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(401000.0);   // AUM = consideration
            assertThat(row.getCell(16).getNumericCellValue()).isEqualTo(2000.0);     // Bonds column
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(1200.0);     // Net Revenue
        }
    }

    @Test
    void anAifDealsOwnSharingPercentIsUsedAndItShowsInTheEndFile() throws Exception {
        long periodId = saveMonth(file(100));   // MF: client 1 net 100, client 2 net 50
        long platform = postJson("/api/platforms", Map.of("name", "AIFPF " + tag), admin, 201).get("id").asLong();
        postJson("/api/platforms/" + platform + "/terms", Map.of("gstInclusive", false, "tdsPercent", 2, "ourShare", 100, "from", month + "-01"), admin, 204);
        // The referral partner's general AIF % is 20; this deal was agreed at 50.
        postJson("/api/partners/" + refId + "/products", Map.of("product", "AIF", "percent", 20, "from", month + "-01"), admin, 204);
        Map<String, Object> aif = new java.util.HashMap<>();
        aif.put("clientId", client1Id);
        aif.put("month", month + "-01");
        aif.put("rmId", rmId);
        aif.put("partnerId", refId);
        aif.put("sharingPercent", 50);
        aif.put("platformId", platform);
        aif.put("fundName", "FUND " + tag);
        aif.put("commitment", 1000000);
        aif.put("markupPercent", 1);    // gross 10,000
        postJson("/api/aif", aif, admin, 201);

        JsonNode run = process(periodId);
        assertThat(run.get("net").decimalValue()).isEqualByComparingTo("10150");     // MF 150 + AIF 10,000
        assertThat(run.get("partners").decimalValue()).isEqualByComparingTo("5000");  // the deal's 50%, not the partner's 20%

        MockHttpServletResponse end = mvc.perform(get("/api/months/" + periodId + "/end-file").session(admin)).andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(end.getContentAsByteArray()))) {
            var row = StreamSupport.stream(wb.getSheetAt(2).spliterator(), false)
                    .filter(r -> r.getCell(31) != null && ("AIF: FUND " + tag).equals(r.getCell(31).getStringCellValue())).findFirst().orElseThrow();
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(1000000.0);   // AUM = commitment
            assertThat(row.getCell(21).getNumericCellValue()).isEqualTo(10000.0);     // AIF column
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(5000.0);      // Net Revenue
        }
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
