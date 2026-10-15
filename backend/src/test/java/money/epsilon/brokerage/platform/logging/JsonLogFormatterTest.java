package money.epsilon.brokerage.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonLogFormatterTest {

    private final JsonLogFormatter formatter = new JsonLogFormatter();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void oneJsonLineWithTheRequestContext() throws Exception {
        LoggingEvent event = event(Map.of(RequestContext.REQUEST_ID, "7K2MQX4P", RequestContext.USER, "asha",
                RequestContext.MODULE, "ingestion", RequestContext.ACTION, "UploadController.start", "other", "x"), "Saved {} rows", 12);

        String line = formatter.format(event);

        assertThat(line).endsWith("\n").doesNotContain("\n{");
        JsonNode log = json.readTree(line);
        assertThat(log.get("level").asText()).isEqualTo("INFO");
        assertThat(log.get("message").asText()).isEqualTo("Saved 12 rows");
        assertThat(log.get("requestId").asText()).isEqualTo("7K2MQX4P");
        assertThat(log.get("user").asText()).isEqualTo("asha");
        assertThat(log.get("module").asText()).isEqualTo("ingestion");
        assertThat(log.get("action").asText()).isEqualTo("UploadController.start");
        assertThat(log.has("other")).as("only the known context keys").isFalse();
        assertThat(log.get("time").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    @Test
    void panShapedTextIsMaskedInMessagesAndErrors() throws Exception {
        LoggingEvent event = event(Map.of(), "Client {} not in master", "abcde1234f");
        event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(
                new IllegalStateException("PAN ZYXWV9876A is taken")));

        JsonNode log = json.readTree(formatter.format(event));

        assertThat(log.get("message").asText()).isEqualTo("Client abcde****f not in master");
        assertThat(log.get("error").asText()).isEqualTo("java.lang.IllegalStateException: PAN ZYXWV****A is taken");
        assertThat(log.get("stack").asText()).contains("ZYXWV****A").doesNotContain("ZYXWV9876A");
    }

    @Test
    void moduleIsTheFirstPackageUnderTheApp() {
        assertThat(RequestContext.moduleOf(JsonLogFormatter.class)).isEqualTo("platform");
        assertThat(RequestContext.moduleOf(String.class)).isEqualTo("other");
        assertThat(RequestContext.newReference()).matches("[2-9A-HJKMNP-Z]{8}");
    }

    private static LoggingEvent event(Map<String, String> mdc, String message, Object... args) {
        LoggerContext context = new LoggerContext();
        LoggingEvent event = new LoggingEvent("test", context.getLogger("money.epsilon.brokerage.Test"), Level.INFO, message, null, args);
        event.setMDCPropertyMap(mdc);
        return event;
    }
}
