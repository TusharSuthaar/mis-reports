package money.epsilon.brokerage.fx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Frankfurter (frankfurter.dev): free European Central Bank reference rates, no key. Only the day and the currency pair
 * are sent. ECB has no rate on weekends and holidays: Frankfurter then answers with the last working day before.
 */
@Component
class FrankfurterRateSource implements RateSource {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterRateSource.class);

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    FrankfurterRateSource(@Value("${mis.fx.url:https://api.frankfurter.dev/v1}") String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
    }

    @Override
    public Optional<Fetched> toInr(String currency, LocalDate day) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + day + "?from=" + currency + "&to=INR"))
                    .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Exchange rate not fetched: Frankfurter answered {} for {} {}", res.statusCode(), currency, day);
                return Optional.empty();
            }
            return parse(mapper.readTree(res.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Exchange rate not fetched for {} {}: {}", currency, day, e.toString());
            return Optional.empty();
        }
    }

    /** The answer looks like {"amount":1.0,"base":"USD","date":"2026-09-25","rates":{"INR":83.12}}. */
    static Optional<Fetched> parse(JsonNode body) {
        JsonNode inr = body.path("rates").path("INR");
        if (!inr.isNumber() || !body.hasNonNull("date")) return Optional.empty();
        BigDecimal amount = body.path("amount").isNumber() && body.get("amount").decimalValue().signum() > 0
                ? body.get("amount").decimalValue() : BigDecimal.ONE;
        BigDecimal rate = inr.decimalValue().divide(amount, 4, RoundingMode.HALF_UP);
        return Optional.of(new Fetched(rate, LocalDate.parse(body.get("date").asText())));
    }
}
