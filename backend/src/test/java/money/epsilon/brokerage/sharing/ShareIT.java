package money.epsilon.brokerage.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import money.epsilon.brokerage.client.service.ClientService;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import money.epsilon.brokerage.mapping.service.AliasService;
import money.epsilon.brokerage.masterimport.TestWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

class ShareIT extends IntegrationTestBase {

    @Autowired
    ClientService clients;

    @Autowired
    UserDetailsService users;

    @Autowired
    AliasService aliases;

    MockHttpSession admin;
    String tag;
    String panA, panB, panC;
    long clientA, clientB, clientC;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
        tag = String.valueOf(System.nanoTime() % 100000000);
        panA = pan('A');
        panB = pan('B');
        panC = pan('C');
        var principal = users.loadUserByUsername(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        try {
            clientA = clients.create(ClientService.INVESTWELL, "A" + tag, null, "CLIENT A " + tag, panA, null);
            clientB = clients.create(ClientService.INVESTWELL, "B" + tag, null, "CLIENT B " + tag, panB, null);
            clientC = clients.create(ClientService.INVESTWELL, "C" + tag, null, "CLIENT C " + tag, panC, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** A valid, test-unique PAN: 5 letters, 4 digits from the tag, 1 letter. */
    String pan(char last) {
        String digits = String.format("%04d", Long.parseLong(tag) % 10000);
        char[] letters = new char[5];
        long t = Long.parseLong(tag);
        for (int i = 0; i < 5; i++) {
            letters[i] = (char) ('A' + (t % 26));
            t /= 26;
        }
        return new String(letters) + digits + last;
    }

    byte[] master(String partnerForB, Object pctForB) {
        return new TestWorkbook()
                .sheet("Master", "Client No", "REGION", "RD", "BRANCH", "RM", "CLIENT NAME", "PAN NO", "Concatenate", "CLIENT TYPE",
                        "BROKER", "TYPE", "%", "POSP", "BROKER", "BROKER 2", "TYPE 2", "% 2")
                .row(1, "R", "RD", "B", "RM", "client a " + tag, panA.toLowerCase(), "", "OFFLINE", "KEFI " + tag + " ", "Sub broker", 0.5, 0, null,
                        "RISHU " + tag, "Referral Partner", 0.2)
                .row(2, "R", "RD", "B", "RM", "CLIENT B " + tag, panB, "", "OFFLINE", partnerForB, "Referral Partner", pctForB, 0, null)
                .row(3, "R", "RD", "B", "RM", "CLIENT C " + tag, panC, "", "OFFLINE", null, null, null, 0, null)
                .row(4, "R", "RD", "B", "RM", "NOT LOADED YET " + tag, "ZZZZZ9999Z", "", "OFFLINE", "KEFI " + tag, "Sub broker", 0.8, 0, null)
                .bytes();
    }

    @Test
    void loadsSharesFromTheMasterAndReloadsOnlyTheDifferences() throws Exception {
        JsonNode p = preview(master("REF " + tag, "NA"));
        assertThat(p.get("error").isNull()).isTrue();
        assertThat(p.get("matchedClients").asInt()).isEqualTo(3);
        assertThat(p.get("toSet").asInt()).isEqualTo(3);
        assertThat(p.get("bothRoles").asInt()).isEqualTo(1);
        assertThat(p.get("zeroPct").asInt()).isEqualTo(1);
        assertThat(p.get("newPartners")).hasSize(3);
        assertThat(p.get("unmatchedCount").asInt()).isEqualTo(1);
        assertThat(p.get("unmatched").get(0).get("reason").asText()).isEqualTo("No client with this PAN yet");
        assertThat(getJson("/api/clients/" + clientA + "/partner", admin, 200)).isEmpty();

        JsonNode r = load(p, "2026-08-01");
        assertThat(r.get("sharesSet").asInt()).isEqualTo(3);
        assertThat(r.get("partnersCreated").asInt()).isEqualTo(3);

        JsonNode current = getJson("/api/shares?clientIds=" + clientA + "," + clientB + "," + clientC, admin, 200);
        JsonNode a = current.get(String.valueOf(clientA));
        assertThat(a).hasSize(2);
        assertThat(a.get(0).get("partnerRole").asText()).isEqualTo("SUB_BROKER");
        assertThat(a.get(0).get("percent").decimalValue()).isEqualByComparingTo("50");
        assertThat(a.get(1).get("partnerRole").asText()).isEqualTo("REFERRAL");
        assertThat(a.get(1).get("percent").decimalValue()).isEqualByComparingTo("20");
        JsonNode b = current.get(String.valueOf(clientB)).get(0);
        assertThat(b.get("percent").decimalValue()).isEqualByComparingTo("0");
        assertThat(b.get("partnerRole").asText()).isEqualTo("REFERRAL");
        assertThat(current.has(String.valueOf(clientC))).isFalse();

        // Same master again: nothing to do.
        JsonNode again = preview(master("REF " + tag, "NA"));
        assertThat(again.get("toSet").asInt()).isZero();
        assertThat(again.get("toEnd").asInt()).isZero();
        assertThat(again.get("unchanged").asInt()).isEqualTo(3);

        // Updated master from September: B gets a %, A unchanged. August keeps the old value.
        JsonNode sep = preview(master("REF " + tag, 0.5));
        assertThat(sep.get("toSet").asInt()).isEqualTo(1);
        load(sep, "2026-09-01");
        JsonNode history = getJson("/api/clients/" + clientB + "/partner", admin, 200);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("percent").decimalValue()).isEqualByComparingTo("50");
        assertThat(history.get(1).get("validTo").asText()).isEqualTo("2026-08-31");

        // Partner removed in the master: the share ends.
        JsonNode oct = preview(master(null, null));
        assertThat(oct.get("toEnd").asInt()).isEqualTo(1);
        load(oct, "2026-10-01");
        assertThat(getJson("/api/clients/" + clientB + "/partner", admin, 200).get(0).get("validTo").asText()).isEqualTo("2026-09-30");
    }

    @Test
    void aDuplicatePartnerIsMergedIntoTheRightOne() throws Exception {
        long right = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "TEST PARTNER AGARWAL " + tag), admin, 201).get("id").asLong();
        long wrong = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "TEST PARTNER AGRAWAL " + tag), admin, 201).get("id").asLong();
        long subBroker = postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", "SB " + tag), admin, 201).get("id").asLong();
        postJson("/api/clients/" + clientA + "/partner", Map.of("partnerId", wrong, "percent", 20, "from", "2026-08-01"), admin, 204);

        postJson("/api/partners/" + wrong + "/merge", Map.of("intoId", subBroker), admin, 409);  // different kind
        postJson("/api/partners/" + wrong + "/merge", Map.of("intoId", wrong), admin, 409);
        JsonNode merged = postJson("/api/partners/" + wrong + "/merge", Map.of("intoId", right, "reason", "spelling"), admin, 200);
        assertThat(merged.get("shares").asInt()).isEqualTo(1);

        JsonNode share = getJson("/api/clients/" + clientA + "/partner", admin, 200).get(0);
        assertThat(share.get("partnerId").asLong()).isEqualTo(right);
        assertThat(share.get("percent").decimalValue()).isEqualByComparingTo("20");
        assertThat(getJson("/api/partners/" + wrong, admin, 200).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(aliases.resolve(ClientService.INVESTWELL, EntityType.PARTNER, "test partner agrawal " + tag)).contains(right);
        postJson("/api/partners/" + wrong + "/merge", Map.of("intoId", right), admin, 409);       // already merged
        assertThat(getJson("/api/audit?entity=PARTNER&size=5", admin, 200).get("items").get(0).get("action").asText()).isEqualTo("MERGE");
    }

    @Test
    void oneClientsPartnerCanBeSetAndEndedByHand() throws Exception {
        long partner = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "HAND " + tag), admin, 201).get("id").asLong();
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", partner, "percent", 45, "from", "2026-08-01"), admin, 204);
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", partner, "percent", 45, "from", "2026-09-01"), admin, 409);
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", partner, "percent", 145, "from", "2026-09-01"), admin, 400);
        assertThat(getJson("/api/shares/partner-counts", admin, 200).get(String.valueOf(partner)).asInt()).isEqualTo(1);

        postJson("/api/clients/" + clientC + "/partner/end", Map.of("role", "SUB_BROKER", "lastDay", "2026-09-30"), admin, 409);
        postJson("/api/clients/" + clientC + "/partner/end", Map.of("role", "REFERRAL", "lastDay", "2026-09-30"), admin, 204);
        assertThat(getJson("/api/shares/partner-counts", admin, 200).has(String.valueOf(partner))).isFalse();
        assertThat(getJson("/api/audit?entity=CLIENT_PARTNER_SHARE&size=5", admin, 200).get("items").get(0).get("action").asText()).isEqualTo("CLOSE");
    }

    @Test
    void aSubBrokerAndAReferralPartnerAreKeptSeparately() throws Exception {
        long sb = postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", "SB " + tag), admin, 201).get("id").asLong();
        long ref1 = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "REF1 " + tag), admin, 201).get("id").asLong();
        long ref2 = postJson("/api/partners", Map.of("type", "REFERRAL", "name", "REF2 " + tag), admin, 201).get("id").asLong();
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", sb, "percent", 0, "from", "2026-08-01"), admin, 204);
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", ref1, "percent", 40, "from", "2026-08-01"), admin, 204);
        // changing the referral partner leaves the sub-broker alone
        postJson("/api/clients/" + clientC + "/partner", Map.of("partnerId", ref2, "percent", 35, "from", "2026-09-01"), admin, 204);

        JsonNode now = getJson("/api/shares?clientIds=" + clientC, admin, 200).get(String.valueOf(clientC));
        assertThat(now).hasSize(2);
        assertThat(now.get(0).get("partnerId").asLong()).isEqualTo(sb);
        assertThat(now.get(1).get("partnerId").asLong()).isEqualTo(ref2);
        assertThat(getJson("/api/clients/" + clientC + "/partner", admin, 200)).hasSize(3);
    }

    /** Master MIS rows: (RM, client name, PAN). Partner columns left blank. */
    byte[] masterWithRms(String[]... rows) {
        TestWorkbook wb = new TestWorkbook().sheet("Master", "Client No", "REGION", "RD", "BRANCH", "RM", "CLIENT NAME", "PAN NO", "Concatenate",
                "CLIENT TYPE", "BROKER", "TYPE", "%", "POSP", "BROKER", "BROKER 2", "TYPE 2", "% 2");
        int n = 1;
        for (String[] r : rows) wb.row(n++, "R", "RD", r.length > 3 ? r[3] : "B", r[0], r[1], r[2], "", "OFFLINE", null, null, null, 0, null);
        return wb.bytes();
    }

    @Test
    void theMasterAddsClientsByPanOrByNameAndSetsTheirRm() throws Exception {
        long region = postJson("/api/regions", Map.of("name", "MR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "MB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long rm1 = postJson("/api/employees", Map.of("name", "MRM ONE " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long rm2 = postJson("/api/employees", Map.of("name", "MRM TWO " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        String newPan = money.epsilon.brokerage.ingestion.FinesseFiles.pan(Long.parseLong(tag) * 10 + 7);
        byte[] file = masterWithRms(
                new String[]{"MRM ONE " + tag, "CLIENT A " + tag, panA},           // existing client, no RM yet: gets one
                new String[]{"mrm  two " + tag, "NEW WITH PAN " + tag, newPan},     // new client by PAN (RM spelt loosely)
                new String[]{"MRM TWO " + tag, "NEW NO PAN " + tag, ""},            // new client by name
                new String[]{"MRM ONE " + tag, "CLIENT B " + tag, panB},
                new String[]{"MRM TWO " + tag, "CLIENT B " + tag, panB},            // the same PAN with another RM: left alone
                new String[]{"NOBODY " + tag, "CLIENT C " + tag, panC});            // unknown RM

        JsonNode p = preview(file, "2026-08-01");
        assertThat(p.get("newClients").asInt()).isEqualTo(2);
        assertThat(p.get("newClientsWithoutPan").asInt()).isEqualTo(1);
        assertThat(p.get("rmChangeCount").asInt()).isEqualTo(1);
        assertThat(p.get("rmChanges").get(0).get("toRm").asText()).isEqualTo("MRM ONE " + tag);
        assertThat(p.get("unknownRms").get(0).get("spelling").asText()).startsWith("NOBODY " + tag);
        assertThat(p.get("problems")).anyMatch(x -> x.asText().contains("different RMs"));

        JsonNode r = load(p, "2026-08-01");
        assertThat(r.get("clientsCreated").asInt()).isEqualTo(2);
        assertThat(r.get("rmChanged").asInt()).isEqualTo(1);
        assertThat(rmOf("NEW NO PAN " + tag)).isEqualTo(rm2);
        assertThat(rmOf("NEW WITH PAN " + tag)).isEqualTo(rm2);
        assertThat(rmOf("CLIENT A " + tag)).isEqualTo(rm1);

        // The same master again: nothing to add or change.
        JsonNode again = preview(file, "2026-08-01");
        assertThat(again.get("newClients").asInt()).isZero();
        assertThat(again.get("rmChangeCount").asInt()).isZero();

        // A corrected master from the same date: client A's RM was wrong from its first day, corrected in place.
        JsonNode fix = preview(masterWithRms(new String[]{"MRM TWO " + tag, "CLIENT A " + tag, panA}), "2026-08-01");
        load(fix, "2026-08-01");
        assertThat(rmOf("CLIENT A " + tag)).isEqualTo(rm2);
        assertThat(getJson("/api/clients/" + clientA + "/rm", admin, 200)).hasSize(1);
    }

    @Test
    void theBranchPicksBetweenTwoBooksOfTheSameRm() throws Exception {
        long region = postJson("/api/regions", Map.of("name", "KR " + tag), admin, 201).get("id").asLong();
        long delhi = postJson("/api/branches", Map.of("name", "KDELHI " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long yn = postJson("/api/branches", Map.of("name", "KYN " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        postJson("/api/employees", Map.of("name", "KASH " + tag, "type", "RM", "branchId", delhi, "from", "2026-01-01"), admin, 201);
        long ynBook = postJson("/api/employees", Map.of("name", "KASH " + tag, "type", "RM", "branchId", yn, "from", "2026-01-01"), admin, 201).get("id").asLong();
        JsonNode p = preview(masterWithRms(new String[]{"KASH " + tag, "CLIENT C " + tag, panC, "kyn  " + tag},
                new String[]{"KASH " + tag, "CLIENT B " + tag, panB, "SOMEWHERE"}), "2026-08-01");
        assertThat(p.get("rmChanges")).singleElement().satisfies(c -> assertThat(c.get("clientId").asLong()).isEqualTo(clientC));
        assertThat(p.get("unknownRms").get(0).get("note").asText()).contains("several RMs");
        load(p, "2026-08-01");
        assertThat(rmOf("CLIENT C " + tag)).isEqualTo(ynBook);
    }

    @Test
    void anUnknownRmSpellingCanBeMappedToAnRmOnce() throws Exception {
        long region = postJson("/api/regions", Map.of("name", "HR " + tag), admin, 201).get("id").asLong();
        long branch = postJson("/api/branches", Map.of("name", "HB " + tag, "regionId", region, "from", "2026-01-01"), admin, 201).get("id").asLong();
        long book = postJson("/api/employees", Map.of("name", "HEAD BOOK " + tag, "type", "RM", "branchId", branch, "from", "2026-01-01"), admin, 201).get("id").asLong();
        byte[] f = masterWithRms(new String[]{"OLD NAME " + tag, "CLIENT B " + tag, panB});
        JsonNode before = preview(f, "2026-08-01");
        assertThat(before.get("unknownRms").get(0).get("spelling").asText()).isEqualTo("OLD NAME " + tag);
        assertThat(before.get("unknownRms").get(0).get("note").asText()).isEqualTo("no RM with this name");
        postJson("/api/share-load/rm-spelling", Map.of("spelling", "OLD NAME " + tag, "rmId", book), admin, 204);
        JsonNode after = preview(f, "2026-08-01");
        assertThat(after.get("unknownRms")).isEmpty();
        assertThat(after.get("rmChanges").get(0).get("toRm").asText()).isEqualTo("HEAD BOOK " + tag);
    }

    long rmOf(String name) throws Exception {
        return getJson("/api/clients?q=" + name, admin, 200)
                .get("items").get(0).get("rmId").asLong();
    }

    @Test
    void financeCanSeeButNotLoad() throws Exception {
        MockHttpSession finance = loginAs("FINANCE");
        getJson("/api/shares?clientIds=" + clientA, finance, 200);
        mvc.perform(withCsrf(multipart("/api/share-load/preview").file(new MockMultipartFile("file", "m.xlsx", null, master(null, null)))
                .param("from", "2026-08-01")).session(finance)).andExpect(status().isForbidden());
    }

    JsonNode preview(byte[] file) throws Exception {
        return preview(file, "2026-08-01");
    }

    JsonNode preview(byte[] file, String from) throws Exception {
        return body(mvc.perform(withCsrf(multipart("/api/share-load/preview").file(new MockMultipartFile("file", "Master_MIS.xlsx", null, file))
                .param("from", from)).session(admin)).andExpect(status().isOk()).andReturn());
    }

    JsonNode load(JsonNode preview, String from) throws Exception {
        long jobId = postJson("/api/share-load", Map.of("token", preview.get("token").asText(), "fileName", "Master_MIS.xlsx", "from", from), admin, 202)
                .get("jobId").asLong();
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            String s = getJson("/api/jobs/" + jobId, admin, 200).get("status").asText();
            return s.equals("DONE") || s.equals("FAILED");
        });
        JsonNode job = getJson("/api/jobs/" + jobId, admin, 200);
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");
        return job.get("result");
    }
}
