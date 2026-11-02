package money.epsilon.brokerage.platform.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Two people editing the same entry (R-99): the screen sends the entry's {@code updatedAt} it opened as the
 * {@code If-Match} header; a save is refused when the entry was saved by someone else since. No header: no check
 * (older screens, scripts). Compared to the millisecond, as the database and the screen may round differently.
 */
public final class EditVersion {

    public static final String HEADER = "If-Match";

    private EditVersion() {
    }

    /** @throws RuleViolationException if {@code opened} is given and the entry has changed since */
    public static void check(Instant current, String opened) {
        if (opened == null || opened.isBlank() || current == null) return;
        Instant seen;
        try {
            seen = Instant.parse(opened.trim().replace("\"", ""));
        } catch (DateTimeParseException e) {
            throw new ValidationException("If-Match must be the entry's updatedAt");
        }
        if (!seen.truncatedTo(ChronoUnit.MILLIS).equals(current.truncatedTo(ChronoUnit.MILLIS))) {
            throw new RuleViolationException("Someone else saved this entry after you opened it. Close it and open it again to see"
                    + " their change, then make yours.");
        }
    }
}
