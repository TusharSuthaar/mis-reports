package money.epsilon.brokerage.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Revenue figures taken exactly as the register has them (R-79) instead of being worked out: gross (excl. GST), TDS, GST
 * and invoice. Net = gross − TDS; a missing invoice is gross + GST; missing TDS / GST are 0. Rounded to paise.
 */
public record GivenFigures(BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal invoice) {

    /** The figures to store, checked: gross is required and nothing may be negative. */
    public Resolved resolve() {
        if (gross == null) throw new IllegalArgumentException("Enter the gross revenue from the register");
        BigDecimal g = p(gross), t = p(tds), s = p(gst);
        if (g.signum() < 0 || t.signum() < 0 || s.signum() < 0) throw new IllegalArgumentException("Register figures cannot be negative");
        if (t.compareTo(g) > 0) throw new IllegalArgumentException("TDS cannot be more than the gross revenue");
        BigDecimal i = invoice == null ? g.add(s) : p(invoice);
        return new Resolved(g, t, s, g.subtract(t), i);
    }

    private static BigDecimal p(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public record Resolved(BigDecimal gross, BigDecimal tds, BigDecimal gst, BigDecimal net, BigDecimal invoice) {
    }
}
