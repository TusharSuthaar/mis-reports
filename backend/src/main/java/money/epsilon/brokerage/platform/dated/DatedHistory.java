package money.epsilon.brokerage.platform.dated;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import money.epsilon.brokerage.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;

/** Shared steps for appending to / ending an assignment history, with audit records. */
public final class DatedHistory {

    private DatedHistory() {
    }

    /** What {@link #setFrom} did. */
    public enum Outcome { UNCHANGED, CORRECTED, CHANGED }

    /**
     * "This value from {@code from}" on a dated history — the one way RM, partner share and similar values change:
     * <ul>
     *   <li>the open row already has the value → {@link Outcome#UNCHANGED} (nothing saved);</li>
     *   <li>the open row starts on {@code from} → it was wrong from its first day: corrected in place, audited CORRECT;</li>
     *   <li>otherwise the open row is closed the day before and a new row starts (CLOSE + CREATE).</li>
     * </ul>
     * The caller checks the locked-months window and any business preconditions first.
     */
    public static <T extends DatedRow> Outcome setFrom(Optional<T> latest, LocalDate from, Predicate<T> hasValue, Consumer<T> correct,
                                                       Supplier<T> newRow, JpaRepository<T, Long> repo, AuditService audit, String entity,
                                                       Function<T, ?> view, String reason) {
        Optional<T> open = latest.filter(row -> row.getValidTo() == null);
        if (open.filter(hasValue).isPresent()) return Outcome.UNCHANGED;
        if (open.filter(row -> row.getValidFrom().equals(from)).isPresent()) {
            T row = open.get();
            Object before = view.apply(row);
            correct.accept(row);
            repo.saveAndFlush(row);
            audit.record(entity, row.getId(), "CORRECT", before, view.apply(row));
            return Outcome.CORRECTED;
        }
        makeRoom(latest, from, repo, audit, entity, view, reason);
        add(newRow.get(), repo, audit, entity, view);
        return Outcome.CHANGED;
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

    /**
     * Ends the open row on {@code lastDay}. A row that would end before its first day never took effect (e.g. a master
     * loaded again from the same date without that value): it is removed instead, audited CANCEL with what it was.
     */
    public static <T extends DatedRow> void end(T open, LocalDate lastDay, JpaRepository<T, Long> repo, AuditService audit,
                                         String entity, Function<T, ?> view, String reason) {
        Object before = view.apply(open);
        if (lastDay.isBefore(open.getValidFrom())) {
            repo.delete(open);
            repo.flush();
            audit.record(entity, open.getId(), "CANCEL", before, closeAudit(null, reason));
            return;
        }
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
