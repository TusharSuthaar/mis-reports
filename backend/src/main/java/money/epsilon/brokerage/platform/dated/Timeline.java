package money.epsilon.brokerage.platform.dated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import money.epsilon.brokerage.platform.web.RuleViolationException;

/**
 * The one way history grows: a new row may only start after the latest row started. If the latest row is
 * still running on the new start date, it is closed the day before. Rows earlier in history are never touched.
 */
public final class Timeline {

    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private Timeline() {
    }

    /** Validates and closes {@code latest} as needed so a row starting {@code from} can be appended. */
    public static void makeRoomFrom(Optional<? extends DatedRow> latest, LocalDate from) {
        if (latest.isEmpty()) return;
        DatedRow row = latest.get();
        if (!from.isAfter(row.getValidFrom())) {
            throw new RuleViolationException("The new date (" + from.format(DISPLAY) + ") must be after the start of the "
                    + "current entry (" + row.getValidFrom().format(DISPLAY) + "). History is not rewritten.");
        }
        if (row.getValidTo() == null || !row.getValidTo().isBefore(from)) {
            row.closeOn(from.minusDays(1));
        }
    }

    /** Ends the open row on {@code lastDay}. */
    public static void end(DatedRow open, LocalDate lastDay) {
        if (open.getValidTo() != null) {
            throw new RuleViolationException("This entry already ended on " + open.getValidTo().format(DISPLAY));
        }
        open.closeOn(lastDay);
    }
}
