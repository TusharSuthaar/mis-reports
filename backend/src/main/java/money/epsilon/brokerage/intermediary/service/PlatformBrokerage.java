package money.epsilon.brokerage.intermediary.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Brokerage on a deal booked through a platform (bonds R-76, AIF R-78): brokerage = base amount × mark-up % × the
 * platform's share for Epsilon (R-75). If the platform's brokerage includes GST, gross = brokerage ÷ (1 + GST %); otherwise
 * gross = brokerage. GST = gross × GST %; invoice = gross + GST; TDS = gross × TDS % when applied; net = gross − TDS.
 * Each rounded to paise (half up).
 */
public record PlatformBrokerage(BigDecimal brokerage, BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal net, BigDecimal invoice) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static PlatformBrokerage of(BigDecimal baseAmount, BigDecimal markupPercent, BigDecimal ourSharePercent, boolean gstInclusive,
                                       BigDecimal gstPercent, boolean tdsApplied, BigDecimal tdsPercent) {
        BigDecimal brokerage = baseAmount.multiply(markupPercent).multiply(ourSharePercent)
                .divide(HUNDRED.multiply(HUNDRED), 2, RoundingMode.HALF_UP);
        BigDecimal gross = gstInclusive
                ? brokerage.multiply(HUNDRED).divide(HUNDRED.add(gstPercent), 2, RoundingMode.HALF_UP)
                : brokerage;
        BigDecimal gst = pct(gross, gstPercent);
        BigDecimal tds = tdsApplied ? pct(gross, tdsPercent) : BigDecimal.ZERO.setScale(2);
        return new PlatformBrokerage(brokerage, gross, tds, gst, gross.subtract(tds), gross.add(gst));
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
