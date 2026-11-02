package money.epsilon.brokerage.platform.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** R-99: a save carries the version the screen opened; a change by someone else since is not overwritten. */
class EditVersionTest {

    private static final Instant SAVED = Instant.parse("2026-10-19T10:17:31.498137Z");

    @Test
    void aSaveFromTheVersionOnRecordGoesThroughAndAnOlderOneIsRefused() {
        assertThatCode(() -> EditVersion.check(SAVED, "2026-10-19T10:17:31.498137Z")).doesNotThrowAnyException();
        assertThatCode(() -> EditVersion.check(SAVED, "2026-10-19T10:17:31.498Z")).doesNotThrowAnyException();   // rounded to ms
        assertThatCode(() -> EditVersion.check(SAVED, "\"2026-10-19T10:17:31.498137Z\"")).doesNotThrowAnyException();   // quoted ETag style
        assertThatThrownBy(() -> EditVersion.check(SAVED, "2026-10-19T10:15:00Z"))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("Someone else saved this entry");
        assertThatThrownBy(() -> EditVersion.check(SAVED, "yesterday")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutAVersionThereIsNothingToCompare() {
        assertThatCode(() -> EditVersion.check(SAVED, null)).doesNotThrowAnyException();
        assertThatCode(() -> EditVersion.check(SAVED, " ")).doesNotThrowAnyException();
        assertThatCode(() -> EditVersion.check(null, "2026-10-19T10:15:00Z")).doesNotThrowAnyException();
    }
}
