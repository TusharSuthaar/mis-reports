package money.epsilon.brokerage.masterimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.stream.StreamSupport;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

class MasterImportIT extends IntegrationTestBase {

    MockHttpSession admin;
    String region;
    String branchA;
    String branchB;
    String rdCode;
    String rmCode;
    String houseCode;
    String partner;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        region = uniqueName("REG");
        branchA = uniqueName("BRA");
        branchB = uniqueName("BRB");
        rdCode = uniqueName("RD");
        rmCode = uniqueName("RM");
        houseCode = uniqueName("HO");
        partner = uniqueName("SUB BROKER");
    }

    TestWorkbook fullWorkbook() {
        LocalDate apr = LocalDate.of(2026, 4, 1);
        return new TestWorkbook()
                .sheet("Regions", "Region").row(region)
                .sheet("Branches", "Branch", "Region", "From").row(branchA, region, apr).row(branchB, region, "01-04-2026")
                .sheet("Employees", "Emp Code", "Name", "Type", "Joined On")
                .row(rdCode, "Regional Director", "RM", "2020-01-01")
                .row(rmCode, "Some RM", "", apr)
                .row(houseCode, "HEAD OFFICE", "HOUSE", null)
                .sheet("RD", "Region", "Emp Code", "From").row(region, rdCode, apr)
                .sheet("RM-Branch", "Emp Code", "Branch", "From")
                .row(rdCode, branchA, apr).row(rmCode, branchB, apr).row(houseCode, branchA, apr)
                .sheet("Partners", "Type", "Name", "GSTIN", "Status").row("Sub-Broker", partner, "", "Active")
                .sheet("Partner-RM", "Partner Type", "Partner Name", "Emp Code", "From").row("SUB_BROKER", partner, rmCode, apr);
    }

    @Test
    void dryRunReportsWhatWouldChangeButSavesNothing() throws Exception {
        JsonNode result = runImport(fullWorkbook().bytes(), true);
        assertThat(result.get("errors")).isEmpty();
        assertThat(result.get("applied").asBoolean()).isFalse();
        assertThat(count(result, "Employees", "created")).isEqualTo(3);
        assertThat(count(result, "RM-Branch", "created")).isEqualTo(3);
        assertThat(getJson("/api/regions", admin, 200).toString()).doesNotContain(region);
    }

    @Test
    void importAppliesEverythingAndReimportingChangesNothing() throws Exception {
        JsonNode first = runImport(fullWorkbook().bytes(), false);
        assertThat(first.get("errors")).isEmpty();
        assertThat(first.get("applied").asBoolean()).isTrue();
        assertThat(count(first, "Regions", "created")).isEqualTo(1);
        assertThat(count(first, "Branches", "created")).isEqualTo(2);
        assertThat(count(first, "RD", "created")).isEqualTo(1);
        assertThat(count(first, "Partner-RM", "created")).isEqualTo(1);

        JsonNode chart = getJson("/api/org?date=2026-04-01", admin, 200);
        JsonNode regionNode = StreamSupport.stream(chart.get("regions").spliterator(), false)
                .filter(r -> r.get("name").asText().equals(region)).findFirst().orElseThrow();
        assertThat(regionNode.get("rd").get("empCode").asText()).isEqualTo(rdCode);
        assertThat(regionNode.get("branches")).hasSize(2);

        JsonNode audit = getJson("/api/audit?entity=EMPLOYEE&action=CREATE&size=10", admin, 200);
        assertThat(audit.get("items").get(0).get("by").asText()).isEqualTo(ADMIN);

        JsonNode second = runImport(fullWorkbook().bytes(), false);
        assertThat(second.get("errors")).isEmpty();
        for (JsonNode sheet : second.get("sheets")) {
            assertThat(sheet.get("created").asInt()).as(sheet.get("sheet").asText()).isZero();
            assertThat(sheet.get("updated").asInt()).as(sheet.get("sheet").asText()).isZero();
        }
    }

    @Test
    void anyBadRowMeansNothingIsSavedAndTheRowIsNamed() throws Exception {
        byte[] file = fullWorkbook()
                .sheet("RD", "Region", "Emp Code", "From").row(region, houseCode, LocalDate.of(2026, 4, 1))
                .bytes();
        JsonNode result = runImport(file, false);
        assertThat(result.get("applied").asBoolean()).isFalse();
        JsonNode error = result.get("errors").get(0);
        assertThat(result.get("errors")).as(result.toString()).hasSize(1);
        assertThat(error.get("sheet").asText()).isEqualTo("RD");
        assertThat(error.get("row").asInt()).isEqualTo(2);
        assertThat(error.get("message").asText()).contains("Only an RM can be an RD");
        assertThat(getJson("/api/regions", admin, 200).toString()).doesNotContain(region);
    }

    @Test
    void unreadableFileAndWrongExtensionAreReported() throws Exception {
        JsonNode result = runImport("not a workbook".getBytes(), false);
        assertThat(result.get("errors").get(0).get("message").asText()).contains("could not be read");

        mvc.perform(withCsrf(multipart("/api/imports/masters").file(new MockMultipartFile("file", "masters.csv", "text/csv", "a,b".getBytes())))
                .session(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void templateDownloadsAndOnlyEditorsCanUpload() throws Exception {
        byte[] template = mvc.perform(get("/api/imports/masters/template").session(loginAs("FINANCE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(WorkbookReader.read(template)).hasSize(SheetSpec.ALL.size());

        mvc.perform(withCsrf(multipart("/api/imports/masters").file(new MockMultipartFile("file", "m.xlsx", null, template)))
                .session(loginAs("FINANCE"))).andExpect(status().isForbidden());
    }

    private JsonNode runImport(byte[] file, boolean dryRun) throws Exception {
        JsonNode accepted = body(mvc.perform(withCsrf(multipart("/api/imports/masters")
                        .file(new MockMultipartFile("file", "masters.xlsx", null, file))
                        .param("dryRun", String.valueOf(dryRun)))
                .session(admin)).andExpect(status().isAccepted()).andReturn());
        long jobId = accepted.get("jobId").asLong();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            String s = getJson("/api/jobs/" + jobId, admin, 200).get("status").asText();
            return s.equals("DONE") || s.equals("FAILED");
        });
        JsonNode job = getJson("/api/jobs/" + jobId, admin, 200);
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");
        return job.get("result");
    }

    private static int count(JsonNode result, String sheet, String field) {
        return StreamSupport.stream(result.get("sheets").spliterator(), false)
                .filter(s -> s.get("sheet").asText().equals(sheet)).findFirst().orElseThrow().get(field).asInt();
    }
}
