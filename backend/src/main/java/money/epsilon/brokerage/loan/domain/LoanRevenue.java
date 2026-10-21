package money.epsilon.brokerage.loan.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A loan payout's commission (R-80): gross = base × payout % + fixed amount, where the base is the sanctioned or the
 * utilised amount; TDS = gross × TDS % when ticked; GST = gross × GST %; net = gross − TDS; invoice = gross + GST.
 * Each figure rounded to paise (half up). No payout % and no fixed amount = no revenue yet (0).
 */
public record LoanRevenue(BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal net, BigDecimal invoice) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static LoanRevenue of(BigDecimal base, BigDecimal payoutPercent, BigDecimal fixedAmount, boolean tdsApplied, BigDecimal tdsPercent,
                                 BigDecimal gstPercent) {
        BigDecimal gross = (payoutPercent == null || base == null ? BigDecimal.ZERO.setScale(2) : pct(base, payoutPercent))
                .add(fixedAmount == null ? BigDecimal.ZERO : fixedAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tds = tdsApplied ? pct(gross, tdsPercent) : BigDecimal.ZERO.setScale(2);
        BigDecimal gst = pct(gross, gstPercent);
        return new LoanRevenue(gross, tds, gst, gross.subtract(tds), gross.add(gst));
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
