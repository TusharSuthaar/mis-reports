package money.epsilon.brokerage.platform.dated;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import money.epsilon.brokerage.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;

/** Shared steps for appending to / ending an assignment history, with audit records. */
public final class DatedHistory {

    private DatedHistory() {
    }

    /**
     * Closes the latest row if it runs into {@code from}, and flushes so the UPDATE reaches the database before the
     * new row's INSERT (Hibernate otherwise orders inserts first and the overlap constraint fires).
     */
    public static <T extends DatedRow> void makeRoom(Optional<T> latest, LocalDate from, JpaRepository<T, Long> repo,
                                              AuditService audit, String entity, Function<T, ?> view, String reason) {
        LocalDate previousEnd = latest.map(DatedRow::getValidTo).orElse(null);
        Object before = latest.map(view).orElse(null);
        Timeline.makeRoomFrom(latest, from);
        latest.filter(row -> !Objects.equals(row.getValidTo(), previousEnd)).ifPresent(row -> {
            repo.saveAndFlush(row);
            audit.record(entity, row.getId(), "CLOSE", before, closeAudit(view.apply(row), reason));
        });
    }

    public static <T extends DatedRow> T add(T row, JpaRepository<T, Long> repo, AuditService audit, String entity,
                                      Function<T, ?> view) {
        T saved = repo.saveAndFlush(row);
        audit.record(entity, saved.getId(), "CREATE", null, view.apply(saved));
        return saved;
    }

    public static <T extends DatedRow> void end(T open, LocalDate lastDay, JpaRepository<T, Long> repo, AuditService audit,
                                         String entity, Function<T, ?> view, String reason) {
        Object before = view.apply(open);
        Timeline.end(open, lastDay);
        repo.saveAndFlush(open);
        audit.record(entity, open.getId(), "CLOSE", before, closeAudit(view.apply(open), reason));
    }

    /** Audit "after" value for a CLOSE: the closed row plus why it was closed. */
    public record Closed(Object row, String closeReason) {
    }

    private static Object closeAudit(Object row, String reason) {
        return new Closed(row, reason);
    }
}
