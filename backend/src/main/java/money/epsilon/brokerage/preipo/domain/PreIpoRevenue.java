package money.epsilon.brokerage.preipo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import money.epsilon.brokerage.platform.web.RuleViolationException;

/**
 * A Pre-IPO deal's revenue (R-93), as in the Pre-IPO register. Mark-up per share: on a BUY the client pays the vendor's base
 * price plus our mark-up (client price − vendor price); on a BUYBACK the vendor pays more than the client gets (vendor price −
 * client price). Brokerage = quantity × mark-up, GST included; gross (without GST) = brokerage × 100 ÷ (100 + GST %);
 * GST = gross × GST %; invoice = gross + GST; TDS = brokerage × TDS % when applied; net = brokerage − TDS. Amount =
 * quantity × client price. Money rounded to paise (half up).
 */
public record PreIpoRevenue(BigDecimal markupPerShare, BigDecimal markupPercent, BigDecimal amount, BigDecimal brokerage, BigDecimal gross,
                            BigDecimal gst, BigDecimal invoice, BigDecimal tds, BigDecimal net) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static PreIpoRevenue of(PreIpoEntry.Kind kind, BigDecimal quantity, BigDecimal clientPrice, BigDecimal vendorPrice,
                                   BigDecimal gstPercent, boolean tdsApplied, BigDecimal tdsPercent) {
        BigDecimal markup = kind == PreIpoEntry.Kind.BUY ? clientPrice.subtract(vendorPrice) : vendorPrice.subtract(clientPrice);
        if (markup.signum() < 0) {
            throw new RuleViolationException(kind == PreIpoEntry.Kind.BUY
                    ? "On a buy the client price must not be below the vendor's base price"
                    : "On a buyback the client price must not be above the price the vendor pays");
        }
        BigDecimal markupPercent = markup.multiply(HUNDRED).divide(vendorPrice, 4, RoundingMode.HALF_UP);
        BigDecimal amount = quantity.multiply(clientPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal brokerage = quantity.multiply(markup).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = brokerage.multiply(HUNDRED).divide(HUNDRED.add(gstPercent), 2, RoundingMode.HALF_UP);
        BigDecimal gst = gross.multiply(gstPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal tds = tdsApplied ? brokerage.multiply(tdsPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        return new PreIpoRevenue(markup, markupPercent, amount, brokerage, gross, gst, gross.add(gst), tds, brokerage.subtract(tds));
    }
}
