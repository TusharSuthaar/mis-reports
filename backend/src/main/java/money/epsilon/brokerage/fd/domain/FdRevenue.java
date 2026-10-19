package money.epsilon.brokerage.fd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An FD's commission (R-70): gross = amount × margin %; TDS = gross × TDS % when ticked; GST = gross × GST %;
 * net = gross − TDS; invoice = gross + GST. Each figure rounded to paise (half up).
 */
public record FdRevenue(BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal net, BigDecimal invoice) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static FdRevenue of(BigDecimal amount, BigDecimal marginPercent, boolean tdsApplied, BigDecimal tdsPercent, BigDecimal gstPercent) {
        BigDecimal gross = pct(amount, marginPercent);
        BigDecimal tds = tdsApplied ? pct(gross, tdsPercent) : BigDecimal.ZERO.setScale(2);
        BigDecimal gst = pct(gross, gstPercent);
        return new FdRevenue(gross, tds, gst, gross.subtract(tds), gross.add(gst));
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
