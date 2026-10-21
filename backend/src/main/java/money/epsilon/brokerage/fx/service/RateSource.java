package money.epsilon.brokerage.fx.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Where exchange rates are fetched from (Frankfurter in the app; a fixed source in tests). */
public interface RateSource {

    /** One unit of {@code currency} in rupees on {@code day} (or the last working day before it); empty when unavailable. */
    Optional<Fetched> toInr(String currency, LocalDate day);

    record Fetched(BigDecimal rate, LocalDate rateDate) {
    }
}
