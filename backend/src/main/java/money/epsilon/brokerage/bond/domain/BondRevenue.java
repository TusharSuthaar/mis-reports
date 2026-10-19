package money.epsilon.brokerage.bond.domain;

import java.math.BigDecimal;
import money.epsilon.brokerage.intermediary.service.PlatformBrokerage;

/** A bond deal's brokerage (R-76): the platform brokerage rule on the investment amount — see {@link PlatformBrokerage}. */
public record BondRevenue(BigDecimal brokerage, BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal net, BigDecimal invoice) {

    public static BondRevenue of(BigDecimal investmentAmount, BigDecimal markupPercent, BigDecimal ourSharePercent, boolean gstInclusive,
                                 BigDecimal gstPercent, boolean tdsApplied, BigDecimal tdsPercent) {
        PlatformBrokerage b = PlatformBrokerage.of(investmentAmount, markupPercent, ourSharePercent, gstInclusive, gstPercent, tdsApplied, tdsPercent);
        return new BondRevenue(b.brokerage(), b.gross(), b.tds(), b.gst(), b.net(), b.invoice());
    }
}
