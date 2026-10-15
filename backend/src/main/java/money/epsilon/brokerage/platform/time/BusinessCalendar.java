package money.epsilon.brokerage.platform.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** "Today" in the business time zone (India by default), not the server's. */
@Component
public class BusinessCalendar {

    private final Clock clock;

    @Autowired
    public BusinessCalendar(@Value("${mis.zone:Asia/Kolkata}") String zone) {
        this(Clock.system(ZoneId.of(zone)));
    }

    BusinessCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
