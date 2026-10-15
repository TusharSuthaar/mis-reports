package money.epsilon.brokerage.platform.activity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.web.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One timeline of what happened, in plain words: background jobs (uploads, imports, processing), operational
 * events from the audit log (save, lock, export…) and unexpected errors. Each row carries the reference that is
 * also in the logs, so "it failed, reference X" leads straight to the log lines.
 */
@Service
public class ActivityService {

    public enum Kind { JOB, EVENT, ERROR }

    /** Plain result words, the same for every kind. */
    public enum Result { WAITING, RUNNING, DONE, FAILED }

    private static final String TIMELINE = """
            SELECT 'JOB' AS kind, j.id AS id, j.type AS what,
                   CASE j.status WHEN 'QUEUED' THEN 'WAITING' ELSE j.status END AS result,
                   CASE WHEN j.status = 'FAILED' THEN j.error ELSE j.payload ->> 'fileName' END AS detail,
                   j.created_by AS by_user, j.created_at AS at, j.finished_at AS finished_at, j.request_id AS reference
              FROM job j
            UNION ALL
            SELECT 'EVENT', a.id, a.entity || ':' || a.action, 'DONE',
                   concat_ws(' · ', coalesce(a.after_json ->> 'month', a.after_json -> 'period' ->> 'month'),
                             a.after_json ->> 'reason'),
                   a.by_user, a.at, NULL, NULL
              FROM audit_log a
             WHERE a.entity || ':' || a.action IN (:events)
            UNION ALL
            SELECT 'ERROR', e.id, coalesce(e.method, '') || ' ' || coalesce(e.path, ''), 'FAILED', e.message,
                   e.by_user, e.at, NULL, e.reference
              FROM error_log e""";

    private final JdbcClient jdbc;
    private final JobService jobs;

    ActivityService(JdbcClient jdbc, JobService jobs) {
        this.jdbc = jdbc;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityView> search(ActivityFilter f, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("events", List.copyOf(ActivityText.EVENTS.keySet()));
        List<String> where = new ArrayList<>();
        if (f.kind() != null) {
            where.add("t.kind = :kind");
            params.put("kind", f.kind().name());
        }
        if (f.by() != null) {
            where.add("lower(t.by_user) = :by");
            params.put("by", f.by().toLowerCase());
        }
        if (f.problemsOnly()) where.add("t.result = 'FAILED'");
        if (f.reference() != null) {
            where.add("upper(t.reference) = :reference");
            params.put("reference", f.reference().toUpperCase());
        }
        if (f.from() != null) {
            where.add("t.at >= :from");
            params.put("from", Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            where.add("t.at < :to");
            params.put("to", Timestamp.from(f.to()));
        }
        String filtered = "FROM (" + TIMELINE + ") t" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where));

        long total = jdbc.sql("SELECT count(*) " + filtered).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<ActivityView> items = jdbc.sql("SELECT t.* " + filtered + " ORDER BY t.at DESC, t.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> {
                    Kind kind = Kind.valueOf(rs.getString("kind"));
                    String what = rs.getString("what");
                    Timestamp finished = rs.getTimestamp("finished_at");
                    return new ActivityView(kind, rs.getLong("id"), title(kind, what), rs.getString("detail"),
                            Result.valueOf(rs.getString("result")), rs.getString("by_user"),
                            rs.getTimestamp("at").toInstant(), finished == null ? null : finished.toInstant(),
                            rs.getString("reference"));
                })
                .list();
        return new PageResponse<>(items, page, size, total);
    }

    /** The numbers at the top of the Activity screen. */
    @Transactional(readOnly = true)
    public Summary summary() {
        Timestamp weekAgo = Timestamp.from(Instant.now().minus(Duration.ofDays(7)));
        long running = jdbc.sql("SELECT count(*) FROM job WHERE status IN ('QUEUED', 'RUNNING')").query(Long.class).single();
        long failedJobs = jdbc.sql("SELECT count(*) FROM job WHERE status = 'FAILED' AND created_at >= :since")
                .param("since", weekAgo).query(Long.class).single();
        long errors = jdbc.sql("SELECT count(*) FROM error_log WHERE at >= :since")
                .param("since", weekAgo).query(Long.class).single();
        return new Summary(running, failedJobs, errors);
    }

    private String title(Kind kind, String what) {
        return switch (kind) {
            case JOB -> jobs.titleOf(what);
            case EVENT -> ActivityText.EVENTS.getOrDefault(what, what);
            case ERROR -> "Unexpected error on " + what.trim();
        };
    }

    /** All optional; {@code from} inclusive, {@code to} exclusive. */
    public record ActivityFilter(Kind kind, String by, boolean problemsOnly, String reference, Instant from, Instant to) {
    }

    public record ActivityView(Kind kind, long id, String title, String detail, Result result, String by,
                               Instant at, Instant finishedAt, String reference) {
    }

    /** Last 7 days for failures and errors. */
    public record Summary(long runningJobs, long failedJobs, long errors) {
    }
}
