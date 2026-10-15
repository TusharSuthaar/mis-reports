package money.epsilon.brokerage.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

class ClientLoadIT extends IntegrationTestBase {

    MockHttpSession admin;
    String tag;
    long patnaRm, sasaramRm, delhiBook, yamunaBook;
    String patnaName, sasaramName, kashish;
    long c1, c2, c3, c4;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime());
        long region = postJson("/api/regions", Map.of("name", "R-" + tag), admin, 201).get("id").asLong();
        long patna = branch("PATNA-" + tag, region);
        long sasaram = branch("SASARAM-" + tag, region);
        long delhi = branch("DELHI-" + tag, region);
        long yamuna = branch("YAMUNA-" + tag, region);
        patnaName = "RM ALPHA " + tag;
        sasaramName = "RM ALPHA_SASARAM " + tag;
        kashish = "RM DELTA " + tag;
        patnaRm = rm(patnaName, patna);
        sasaramRm = rm(sasaramName, sasaram);
        delhiBook = rm(kashish, delhi);
        yamunaBook = rm(kashish, yamuna);
        c1 = Long.parseLong(tag.substring(tag.length() - 9)) * 10 + 1;
        c2 = c1 + 1;
        c3 = c1 + 2;
        c4 = c1 + 3;
    }

    long branch(String name, long region) throws Exception {
        return postJson("/api/branches", Map.of("name", name, "regionId", region, "from", "2026-08-01"), admin, 201).get("id").asLong();
    }

    long rm(String name, long branch) throws Exception {
        return postJson("/api/employees", Map.of("name", name, "type", "RM", "branchId", branch, "from", "2026-08-01"), admin, 201)
                .get("id").asLong();
    }

    byte[] augustFile() {
        return new IncomeWorkbook()
                .client("CLIENT ONE", 118, "ABCDE1234F", "", patnaName.toLowerCase(), "", c1, 18, 100)
                .client("CLIENT TWO", 59, "", String.valueOf(c1), "Rm Alpha - Sasaram " + tag, "ZEDD XAVIER " + tag, c2, 9, 50)
                .client("CLIENT THREE", 11.8, "", "", "Rm Alpha - Sasaram " + tag, "ZEDD QUILLON XAVIER " + tag, c3, 1.8, 10)
                .client("CLIENT FOUR", 1.18, "", "", kashish, "", c4, 0.18, 1)
                .total(189.98, 28.98, 161)
                .bytes();
    }

    @Test
    void previewLoadAndReloadTheAugustFile() throws Exception {
        // Preview: nothing saved; spellings classified.
        JsonNode preview = preview(augustFile());
        assertThat(preview.get("errors")).isEmpty();
        assertThat(preview.get("newClients").asInt()).isEqualTo(4);
        assertThat(rmLine(preview, patnaName.toLowerCase()).get("status").asText()).isEqualTo("MATCHED");
        assertThat(rmLine(preview, "Rm Alpha - Sasaram " + tag).get("status").asText()).isEqualTo("UNKNOWN");
        JsonNode several = rmLine(preview, kashish);
        assertThat(several.get("status").asText()).isEqualTo("SEVERAL");
        assertThat(several.get("candidates")).hasSize(2);
        assertThat(partnerLine(preview, "ZEDD QUILLON XAVIER " + tag).get("status").asText()).isEqualTo("UNKNOWN");
        assertThat(getJson("/api/clients?q=" + c1, admin, 200).get("total").asInt()).isZero();

        // Load: Sasaram spelling → the Sasaram book; AARIN AMIT merged into ZEDD XAVIER; Kashish left for later.
        JsonNode result = load(preview, Map.of("Rm Alpha - Sasaram " + tag, sasaramRm),
                Map.of("ZEDD QUILLON XAVIER " + tag, "ZEDD XAVIER " + tag));
        assertThat(result.get("clientsCreated").asInt()).isEqualTo(4);
        assertThat(result.get("rmAssigned").asInt()).isEqualTo(3);
        assertThat(result.get("needsRm").asInt()).isEqualTo(1);
        assertThat(result.get("partnersCreated").asInt()).isEqualTo(1);
        assertThat(result.get("spellingsRemembered").asInt()).isEqualTo(2);
        assertThat(result.get("familyHeadsLinked").asInt()).isEqualTo(1);

        JsonNode two = client(c2);
        assertThat(two.get("rmId").asLong()).isEqualTo(sasaramRm);
        assertThat(two.get("branchName").asText()).isEqualTo("SASARAM-" + tag);
        assertThat(client(c4).get("rmId").isNull()).isTrue();
        assertThat(getJson("/api/clients?needsRm=true&q=" + c4, admin, 200).get("total").asInt()).isEqualTo(1);

        // Next time the same spellings resolve by themselves and nothing is created twice.
        JsonNode again = preview(augustFile());
        assertThat(again.get("newClients").asInt()).isZero();
        assertThat(again.get("existingClients").asInt()).isEqualTo(4);
        assertThat(rmLine(again, "Rm Alpha - Sasaram " + tag).get("status").asText()).isEqualTo("REMEMBERED");
        assertThat(partnerLine(again, "ZEDD QUILLON XAVIER " + tag).get("status").asText()).isEqualTo("REMEMBERED");
        assertThat(partnerLine(again, "ZEDD QUILLON XAVIER " + tag).get("partnerName").asText()).isEqualTo("ZEDD XAVIER " + tag);
        JsonNode second = load(again, Map.of(), Map.of());
        assertThat(second.get("clientsCreated").asInt()).isZero();
        assertThat(second.get("rmAssigned").asInt()).isZero();
        assertThat(second.get("partnersCreated").asInt()).isZero();
    }

    @Test
    void changingAClientsRmKeepsTheOldMonthWithTheOldRm() throws Exception {
        load(preview(augustFile()), Map.of("Rm Alpha - Sasaram " + tag, sasaramRm), Map.of());
        long id = client(c1).get("id").asLong();

        postJson("/api/clients/" + id + "/rm", Map.of("rmId", sasaramRm, "from", "2026-09-01"), admin, 204);

        JsonNode history = getJson("/api/clients/" + id + "/rm", admin, 200);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("targetId").asLong()).isEqualTo(sasaramRm);
        assertThat(history.get(1).get("targetId").asLong()).isEqualTo(patnaRm);
        assertThat(history.get(1).get("validFrom").asText()).isEqualTo("2026-08-01");
        assertThat(history.get(1).get("validTo").asText()).isEqualTo("2026-08-31");
        postJson("/api/clients/" + id + "/rm", Map.of("rmId", patnaRm, "from", "2026-08-15"), admin, 409);
    }

    @Test
    void severalClientsCanBeGivenAnRmInOneGo() throws Exception {
        load(preview(augustFile()), Map.of(), Map.of());
        List<Long> waiting = List.of(client(c2).get("id").asLong(), client(c3).get("id").asLong(), client(c4).get("id").asLong());
        JsonNode r = postJson("/api/clients/rm", Map.of("clientIds", waiting, "rmId", yamunaBook, "from", "2026-08-01"), admin, 200);
        assertThat(r.get("changed").asInt()).isEqualTo(3);
        assertThat(client(c4).get("branchName").asText()).isEqualTo("YAMUNA-" + tag);
    }

    @Test
    void panIsMaskedForHoAndAFileThatDoesNotAddUpCannotBeLoaded() throws Exception {
        load(preview(augustFile()), Map.of(), Map.of());
        assertThat(client(c1).get("pan").asText()).isEqualTo("ABCDE1234F");
        JsonNode masked = getJson("/api/clients?q=" + c1, loginAs("HO"), 200).get("items").get(0);
        assertThat(masked.get("pan").asText()).isEqualTo("ABCDE****F");

        byte[] bad = new IncomeWorkbook().client("X", 118, "", "", patnaName, "", c1 + 100, 18, 100).total(1, 1, 1).bytes();
        JsonNode p = preview(bad);
        assertThat(p.get("errors").get(0).get("message").asText()).contains("do not add up");
        long jobId = postJson("/api/client-load", Map.of("token", p.get("token").asText(), "fileName", "bad.xlsx", "from", "2026-08-01"), admin, 202)
                .get("jobId").asLong();
        JsonNode job = awaitJob(jobId);
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("error").asText()).contains("cannot be loaded");
    }

    @Test
    void onlyEditorsCanLoadAndTokensMustComeFromAPreview() throws Exception {
        mvc.perform(withCsrf(multipart("/api/client-load/preview").file(new MockMultipartFile("file", "a.xlsx", null, augustFile())))
                .session(loginAs("FINANCE"))).andExpect(status().isForbidden());
        postJson("/api/client-load", Map.of("token", "../../etc/passwd", "fileName", "x", "from", "2026-08-01"), admin, 400);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    JsonNode preview(byte[] file) throws Exception {
        return body(mvc.perform(withCsrf(multipart("/api/client-load/preview").file(new MockMultipartFile("file", "aug.xlsx", null, file)))
                .session(admin)).andExpect(status().isOk()).andReturn());
    }

    JsonNode load(JsonNode preview, Map<String, Long> rmChoices, Map<String, String> partnerNames) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("token", preview.get("token").asText());
        req.put("fileName", "aug.xlsx");
        req.put("from", "2026-08-01");
        req.put("rmChoices", rmChoices);
        req.put("partnerNames", partnerNames);
        JsonNode job = awaitJob(postJson("/api/client-load", req, admin, 202).get("jobId").asLong());
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");
        return job.get("result");
    }

    JsonNode awaitJob(long jobId) throws Exception {
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            String s = getJson("/api/jobs/" + jobId, admin, 200).get("status").asText();
            return s.equals("DONE") || s.equals("FAILED");
        });
        return getJson("/api/jobs/" + jobId, admin, 200);
    }

    JsonNode client(long code) throws Exception {
        JsonNode page = getJson("/api/clients?q=" + code, admin, 200);
        assertThat(page.get("total").asInt()).as("client " + code).isEqualTo(1);
        return page.get("items").get(0);
    }

    static JsonNode rmLine(JsonNode preview, String spelling) {
        return find(preview.get("rms"), spelling);
    }

    static JsonNode partnerLine(JsonNode preview, String spelling) {
        return find(preview.get("subBrokers"), spelling);
    }

    private static JsonNode find(JsonNode lines, String spelling) {
        return StreamSupport.stream(lines.spliterator(), false).filter(l -> l.get("spelling").asText().equals(spelling))
                .findFirst().orElseThrow(() -> new AssertionError("no line for " + spelling + " in " + lines));
    }
}
