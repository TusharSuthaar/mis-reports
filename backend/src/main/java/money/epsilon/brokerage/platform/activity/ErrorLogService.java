package money.epsilon.brokerage.platform.activity;

import java.time.Duration;
import java.time.Instant;
import money.epsilon.brokerage.platform.crypto.PanCipher;
import money.epsilon.brokerage.platform.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps unexpected errors (by reference) so they show on the Activity screen next to the job that failed.
 * Messages are PAN-masked; stack traces stay in the logs. Rows older than the retention are removed nightly.
 */
@Service
public class ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogService.class);
    private static final int MAX_MESSAGE = 2000;
    private static final int MAX_PATH = 300;

    private final JdbcClient jdbc;
    private final int retentionDays;

    ErrorLogService(JdbcClient jdbc, @Value("${mis.logs.error-retention-days:400}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    /** Never throws: failing to note an error must not hide the error itself. */
    public void record(String reference, String method, String path, int status, String message) {
        try {
            String text = PanCipher.maskAll(message == null ? "(no message)" : message);
            jdbc.sql("""
                    INSERT INTO error_log (reference, by_user, method, path, status, message)
                    VALUES (:reference, :by, :method, :path, :status, :message)""")
                    .param("reference", reference)
                    .param("by", CurrentUser.username())
                    .param("method", method)
                    .param("path", cut(path, MAX_PATH))
                    .param("status", status)
                    .param("message", cut(text, MAX_MESSAGE))
                    .update();
        } catch (RuntimeException e) {
            log.warn("Could not record error {} in error_log", reference, e);
        }
    }

    /** 02:30 every night: errors past retention go (the audit log is never trimmed). */
    @Scheduled(cron = "0 30 2 * * *", zone = "${mis.zone}")
    public void purgeOld() {
        int removed = jdbc.sql("DELETE FROM error_log WHERE at < :cutoff")
                .param("cutoff", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(retentionDays))))
                .update();
        if (removed > 0) log.info("Removed {} error record(s) older than {} days", removed, retentionDays);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
