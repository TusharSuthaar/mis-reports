package money.epsilon.brokerage.partner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

class PartnerIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAdmin();
    }

    /** A PAN no other test uses: 5 letters, 4 digits, 1 letter. */
    static String freshPan() {
        long n = System.nanoTime();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < 5; i++) letters.append((char) ('A' + (n >> (i * 5)) % 26));
        return letters + String.format("%04d", (n / 7) % 10000) + (char) ('A' + (n / 13) % 26);
    }

    long partner(String type, String name, String pan) throws Exception {
        return postJson("/api/partners", Map.of("type", type, "name", name, "pan", pan), admin, 201).get("id").asLong();
    }

    long rm(String type) throws Exception {
        return postJson("/api/employees", Map.of("empCode", uniqueName("E"), "name", uniqueName("RM"), "type", type), admin, 201)
                .get("id").asLong();
    }

    @Test
    void panIsEncryptedAtRestAndMaskedForAnyoneButFinanceAndAdmin() throws Exception {
        String pan = freshPan();
        long id = partner("SUB_BROKER", uniqueName("SB"), pan.toLowerCase());

        String stored = jdbc.sql("SELECT pan_encrypted FROM partner WHERE id = ?").param(id).query(String.class).single();
        assertThat(stored).doesNotContain(pan);

        assertThat(getJson("/api/partners/" + id, admin, 200).get("pan").asText()).isEqualTo(pan);
        assertThat(getJson("/api/partners/" + id, loginAs("FINANCE"), 200).get("pan").asText()).isEqualTo(pan);
        assertThat(getJson("/api/partners/" + id, loginAs("HO"), 200).get("pan").asText())
                .isEqualTo(pan.substring(0, 5) + "****" + pan.substring(9));

        JsonNode audit = getJson("/api/audit?entity=PARTNER&entityId=" + id, admin, 200);
        assertThat(audit.toString()).doesNotContain(pan).contains(pan.substring(0, 5) + "****");
    }

    @Test
    void samePanCannotBeUsedTwiceForTheSameType() throws Exception {
        String pan = freshPan();
        partner("SUB_BROKER", uniqueName("SB"), pan);
        postJson("/api/partners", Map.of("type", "SUB_BROKER", "name", uniqueName("SB"), "pan", pan), admin, 409);
        postJson("/api/partners", Map.of("type", "POSP", "name", uniqueName("P"), "pan", pan), admin, 201);
    }

    @Test
    void invalidPanAndGstinAreRejected() throws Exception {
        postJson("/api/partners", Map.of("type", "REFERRAL", "name", uniqueName("R"), "pan", "ABC123"), admin, 400);
        postJson("/api/partners", Map.of("type", "REFERRAL", "name", uniqueName("R"), "gstin", "12345"), admin, 400);
        postJson("/api/partners", Map.of("type", "REFERRAL", "name", uniqueName("R"), "gstin", "27abcde1234f1z5"), admin, 201);
    }

    @Test
    void partnerLinksToRmsOverTimeAndOnlyToRms() throws Exception {
        long p = partner("REFERRAL", uniqueName("REF"), freshPan());
        long rm1 = rm("RM");
        long rm2 = rm("RM");
        long house = rm("HOUSE");

        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rm1, "from", "2026-04-01"), admin, 201);
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rm2, "from", "2026-04-01"), admin, 201);
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", house, "from", "2026-04-01"), admin, 409);
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rm1, "from", "2026-05-01"), admin, 409);

        JsonNode links = getJson("/api/partners/" + p + "/rm-links", admin, 200);
        assertThat(links).hasSize(2);
        assertThat(getJson("/api/partners/" + p, admin, 200).get("currentRmLinks").asLong()).isEqualTo(2);

        long linkId = links.get(0).get("id").asLong();
        postJson("/api/partners/" + p + "/rm-links/" + linkId + "/end", Map.of("lastDay", "2026-06-30"), admin, 204);
        assertThat(getJson("/api/partners/" + p, admin, 200).get("currentRmLinks").asLong()).isEqualTo(1);
    }

    @Test
    void anRmLeavingEndsTheirPartnerLinks() throws Exception {
        long p = partner("SUB_BROKER", uniqueName("SB"), freshPan());
        long rm = rm("RM");
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rm, "from", "2026-04-01"), admin, 201);
        postJson("/api/employees/" + rm + "/exit", Map.of("lastDay", "2026-08-31"), admin, 200);

        JsonNode link = getJson("/api/partners/" + p + "/rm-links", admin, 200).get(0);
        assertThat(link.get("validTo").asText()).isEqualTo("2026-08-31");
    }

    @Test
    void inactivePartnersCannotBeLinkedAndTypeCannotChange() throws Exception {
        long p = partner("SUB_BROKER", uniqueName("SB"), freshPan());
        patchJson("/api/partners/" + p, Map.of("status", "INACTIVE"), admin, 200);
        postJson("/api/partners/" + p + "/rm-links", Map.of("rmId", rm("RM"), "from", "2026-04-01"), admin, 409);
        // the update API has no type field at all; a type sent anyway is ignored by JSON binding
        assertThat(patchJson("/api/partners/" + p, Map.of("type", "POSP"), admin, 200).get("type").asText()).isEqualTo("SUB_BROKER");
    }
}
