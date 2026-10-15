package money.epsilon.brokerage.platform.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * One JSON object per log line: time, level, the request context (requestId, user, module, action, jobId),
 * logger, message, and the error if any. Anything shaped like a PAN is masked on the way out, whoever logged it.
 * Wired in application.yml ({@code logging.structured.format.console}).
 */
public class JsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final JsonFactory JSON = new JsonFactory();
    static final int MAX_STACK = 12_000;

    @Override
    public String format(ILoggingEvent event) {
        StringWriter out = new StringWriter(256);
        try (JsonGenerator g = JSON.createGenerator(out)) {
            g.writeStartObject();
            g.writeStringField("time", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            g.writeStringField("level", event.getLevel().toString());
            Map<String, String> mdc = event.getMDCPropertyMap();
            for (String key : RequestContext.KEYS) {
                String value = mdc == null ? null : mdc.get(key);
                if (value != null) g.writeStringField(key, PanCipher.maskAll(value));
            }
            g.writeStringField("logger", event.getLoggerName());
            g.writeStringField("thread", event.getThreadName());
            g.writeStringField("message", PanCipher.maskAll(event.getFormattedMessage()));
            IThrowableProxy error = event.getThrowableProxy();
            if (error != null) {
                g.writeStringField("error", PanCipher.maskAll(error.getClassName() + ": " + error.getMessage()));
                String stack = ThrowableProxyUtil.asString(error);
                if (stack.length() > MAX_STACK) stack = stack.substring(0, MAX_STACK) + "\n\t... (cut)";
                g.writeStringField("stack", PanCipher.maskAll(stack));
            }
            g.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.append('\n').toString();
    }
}
