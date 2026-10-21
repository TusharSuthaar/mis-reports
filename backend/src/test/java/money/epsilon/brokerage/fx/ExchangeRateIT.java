package money.epsilon.brokerage.fx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** R-84: exchange rates fetched once per day and kept, or set by hand. */
class ExchangeRateIT extends IntegrationTestBase {

    @Test
    void aDaysRateIsFetchedOnceKeptAndCanBeSetByHand() throws Exception {
        MockHttpSession admin = loginAdmin();
        // 2026-09-27 is a Sunday: the source answers with Friday's rate, kept against the Sunday.
        JsonNode sunday = postJson("/api/exchange-rates/USD/lookup?date=2026-09-27", Map.of(), admin, 200);
        assertThat(sunday.get("rate").decimalValue()).isEqualByComparingTo("83.5");
        assertThat(sunday.get("rateDate").asText()).isEqualTo("2026-09-25");
        assertThat(sunday.get("source").asText()).isEqualTo("ECB");
        assertThat(sunday.get("note").asText()).contains("last working day");

        // Set by hand: later look-ups of that day use it.
        putJson("/api/exchange-rates/USD/2026-09-27", Map.of("rate", 95.97, "reason", "fund statement"), admin, 200);
        JsonNode again = postJson("/api/exchange-rates/USD/lookup?date=2026-09-27", Map.of(), admin, 200);
        assertThat(again.get("rate").decimalValue()).isEqualByComparingTo("95.97");
        assertThat(again.get("source").asText()).isEqualTo("MANUAL");
        assertThat(getJson("/api/exchange-rates/USD", admin, 200).toString()).contains("fund statement");

        // Cannot be fetched: no rate, a note, nothing kept.
        JsonNode none = postJson("/api/exchange-rates/USD/lookup?date=2000-01-01", Map.of(), admin, 200);
        assertThat(none.get("rate").isNull()).isTrue();
        assertThat(none.get("note").asText()).contains("type it");

        // Today, before ECB publishes today's rate: the latest one is used but not kept against today.
        JsonNode today = postJson("/api/exchange-rates/USD/lookup", Map.of(), admin, 200);
        assertThat(today.get("rate").decimalValue()).isEqualByComparingTo("83.4");
        assertThat(today.get("note").asText()).contains("not out yet");
        assertThat(getJson("/api/exchange-rates/USD", admin, 200).toString()).doesNotContain("\"day\":\"" + today.get("day").asText() + "\"");

        // Finance reads the list but does not change it; other currencies are refused.
        getJson("/api/exchange-rates/USD", loginAs("FINANCE"), 200);
        putJson("/api/exchange-rates/USD/2026-09-28", Map.of("rate", 90), loginAs("FINANCE"), 403);
        getJson("/api/exchange-rates/EUR", admin, 400);
    }
}
