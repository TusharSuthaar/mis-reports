package money.epsilon.brokerage.period.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import money.epsilon.brokerage.platform.dated.ChangeWindow;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Refuses dated changes inside a locked month; re-opens processed months the change reaches. */
@Component
class LockedMonthsGuard implements ChangeWindow {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final JdbcClient jdbc;

    LockedMonthsGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void beforeChange(LocalDate firstAffectedDay) {
        // A change runs forward from its date, so it reaches every month ending on or after that date.
        LocalDate lastLocked = jdbc.sql("SELECT to_date FROM period WHERE status = 'LOCKED' AND to_date >= ? ORDER BY to_date DESC LIMIT 1")
                .param(firstAffectedDay).query(LocalDate.class).list().stream().findFirst().orElse(null);
        if (lastLocked != null) {
            throw new RuleViolationException("A change from " + firstAffectedDay.format(DISPLAY) + " would reach a locked month. Changes must"
                    + " start after " + lastLocked.format(DISPLAY) + " (or ask finance to unlock the month).");
        }
        jdbc.sql("UPDATE period SET status = 'OPEN' WHERE status = 'PROCESSED' AND to_date >= ?").param(firstAffectedDay).update();
    }

    @Override
    public void beforeEntryChange(LocalDate day) {
        LocalDate month = day.withDayOfMonth(1);
        // The row lock makes an entry change wait for a process or lock of the same month that is running, and the other way round.
        boolean locked = jdbc.sql("SELECT status FROM period WHERE month = ? FOR UPDATE").param(month).query(String.class).list()
                .contains("LOCKED");
        if (locked) {
            throw new RuleViolationException(month.format(MONTH) + " is locked, so its entries cannot change. Ask finance to unlock the month first.");
        }
        jdbc.sql("UPDATE period SET status = 'OPEN' WHERE month = ? AND status = 'PROCESSED'").param(month).update();
    }
}
