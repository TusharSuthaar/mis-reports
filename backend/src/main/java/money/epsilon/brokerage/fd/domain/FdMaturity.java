package money.epsilon.brokerage.fd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An FD's maturity value from its period in months (R-72), as the FD companies' certificates work it out:
 * <ul>
 *   <li>Cumulative: interest compounds n times a year (yearly 1 — the default —, half-yearly 2, quarterly 4) for every
 *       full compounding period, and simple interest runs on the months left over:
 *       P × (1 + r/n)^(full periods) × (1 + r × left-over months / 12);</li>
 *   <li>interest paid out (monthly … yearly): P + P × r × months / 12 — the principal plus all interest received.</li>
 * </ul>
 * Rounded to paise (half up).
 */
public final class FdMaturity {

    private FdMaturity() {
    }

    public static BigDecimal value(BigDecimal amount, BigDecimal ratePercent, int months, FdEntry.InterestFrequency frequency,
                                   FdEntry.Compounding compounding) {
        double p = amount.doubleValue();
        double r = ratePercent.doubleValue() / 100.0;
        double value;
        if (frequency == FdEntry.InterestFrequency.CUMULATIVE) {
            int n = (compounding == null ? FdEntry.Compounding.YEARLY : compounding).timesPerYear();
            int monthsPerPeriod = 12 / n;
            int full = months / monthsPerPeriod;
            int left = months - full * monthsPerPeriod;
            value = p * Math.pow(1 + r / n, full) * (1 + r * left / 12.0);
        } else {
            value = p + p * r * months / 12.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
