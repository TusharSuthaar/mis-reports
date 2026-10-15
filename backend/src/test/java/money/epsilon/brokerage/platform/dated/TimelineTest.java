package money.epsilon.brokerage.platform.dated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.junit.jupiter.api.Test;

class TimelineTest {

    static final class Row extends DatedRow {
        Row(LocalDate from, LocalDate to) {
            super(from, to, "test");
        }
    }

    private static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);
    private static final LocalDate JUL_1 = LocalDate.of(2026, 7, 1);

    @Test
    void appendingClosesTheOpenRowTheDayBefore() {
        Row open = new Row(APR_1, null);
        Timeline.makeRoomFrom(Optional.of(open), JUL_1);
        assertThat(open.getValidTo()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(open.covers(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(open.covers(JUL_1)).isFalse();
    }

    @Test
    void historyIsNotRewritten() {
        Row open = new Row(JUL_1, null);
        assertThatThrownBy(() -> Timeline.makeRoomFrom(Optional.of(open), APR_1)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> Timeline.makeRoomFrom(Optional.of(open), JUL_1)).isInstanceOf(RuleViolationException.class);
        assertThat(open.getValidTo()).isNull();
    }

    @Test
    void aRowThatAlreadyEndedBeforeTheNewDateIsLeftAlone() {
        LocalDate end = LocalDate.of(2026, 5, 31);
        Row closed = new Row(APR_1, end);
        Timeline.makeRoomFrom(Optional.of(closed), JUL_1);
        assertThat(closed.getValidTo()).isEqualTo(end);
    }

    @Test
    void aClosedRowRunningPastTheNewDateIsShortened() {
        Row closed = new Row(APR_1, LocalDate.of(2026, 12, 31));
        Timeline.makeRoomFrom(Optional.of(closed), JUL_1);
        assertThat(closed.getValidTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void endingTwiceIsRefused() {
        Row open = new Row(APR_1, null);
        Timeline.end(open, JUL_1);
        assertThatThrownBy(() -> Timeline.end(open, JUL_1)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void cannotEndBeforeItStarts() {
        Row open = new Row(JUL_1, null);
        assertThatThrownBy(() -> Timeline.end(open, APR_1)).isInstanceOf(IllegalArgumentException.class);
    }
}
