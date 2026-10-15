package money.epsilon.brokerage.platform.logging;

import java.security.SecureRandom;
import java.util.List;
import org.slf4j.MDC;

/**
 * What every log line knows about the work it belongs to (kept in the SLF4J MDC). The request ID doubles as the
 * <b>reference</b> users quote: it is in the response header, every error body, the job record and every log line.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String USER = "user";
    public static final String MODULE = "module";
    public static final String ACTION = "action";
    public static final String JOB_ID = "jobId";
    /** In the order they are written to the JSON log. */
    public static final List<String> KEYS = List.of(REQUEST_ID, USER, MODULE, ACTION, JOB_ID);

    public static final String HEADER = "X-Request-Id";

    /** No 0/O or 1/I/L: easy to read out over the phone. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE_PACKAGE = "money.epsilon.brokerage.";

    private RequestContext() {
    }

    /** A new reference, e.g. {@code 7K2MQX4P}. */
    public static String newReference() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new String(out);
    }

    /** The current request's (or job's) reference; null outside any request or job. */
    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    /** The current reference, or a fresh one for work that started outside a request (start-up, schedules). */
    public static String requestIdOrNew() {
        String id = requestId();
        return id != null ? id : newReference();
    }

    /** Module of a class = first package segment under money.epsilon.brokerage (e.g. "ingestion"). */
    public static String moduleOf(Class<?> type) {
        String pkg = type.getPackageName();
        if (!pkg.startsWith(BASE_PACKAGE)) return "other";
        String rest = pkg.substring(BASE_PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
