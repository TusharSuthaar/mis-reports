package money.epsilon.brokerage.preipo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.junit.jupiter.api.Test;

/** R-93: the Pre-IPO register's formulas. */
class PreIpoRevenueTest {

    private static final BigDecimal GST = new BigDecimal("18");
    private static final BigDecimal TDS = new BigDecimal("5");

    @Test
    void aBuyAddsTheMarkupAndTheRegistersFormulasFollow() {
        // 100 shares, vendor base 100, client pays 110: mark-up 10 a share.
        PreIpoRevenue r = PreIpoRevenue.of(PreIpoEntry.Kind.BUY, new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("100"), GST, true, TDS);
        assertThat(r.markupPerShare()).isEqualByComparingTo("10");
        assertThat(r.markupPercent()).isEqualByComparingTo("10");
        assertThat(r.amount()).isEqualByComparingTo("11000");     // Investment Amount = qty × client price
        assertThat(r.brokerage()).isEqualByComparingTo("1000");   // Gross Brk = qty × mark-up (incl. GST)
        assertThat(r.gross()).isEqualByComparingTo("847.46");     // Without GST = 1000 × 100 / 118
        assertThat(r.gst()).isEqualByComparingTo("152.54");       // 18% of without GST
        assertThat(r.invoice()).isEqualByComparingTo("1000.00");  // without GST + GST
        assertThat(r.tds()).isEqualByComparingTo("50.00");        // 5% of Gross Brk
        assertThat(r.net()).isEqualByComparingTo("950.00");       // Net Revenue = Gross Brk − TDS
    }

    @Test
    void aBuybackTakesTheMarkupOffWhatTheClientGets() {
        // The vendor pays 100 a share, the client gets 95: mark-up 5 a share on 50 shares.
        PreIpoRevenue r = PreIpoRevenue.of(PreIpoEntry.Kind.BUYBACK, new BigDecimal("50"), new BigDecimal("95"), new BigDecimal("100"), GST, true, TDS);
        assertThat(r.markupPerShare()).isEqualByComparingTo("5");
        assertThat(r.amount()).isEqualByComparingTo("4750");
        assertThat(r.brokerage()).isEqualByComparingTo("250");
        assertThat(r.gross()).isEqualByComparingTo("211.86");
        assertThat(r.gst()).isEqualByComparingTo("38.13");
        assertThat(r.tds()).isEqualByComparingTo("12.50");
        assertThat(r.net()).isEqualByComparingTo("237.50");
        // No TDS when not applied.
        assertThat(PreIpoRevenue.of(PreIpoEntry.Kind.BUYBACK, new BigDecimal("50"), new BigDecimal("95"), new BigDecimal("100"), GST, false, TDS).tds())
                .isEqualByComparingTo("0");
    }

    @Test
    void aMarkupBelowZeroIsRefused() {
        assertThatThrownBy(() -> PreIpoRevenue.of(PreIpoEntry.Kind.BUY, BigDecimal.ONE, new BigDecimal("90"), new BigDecimal("100"), GST, true, TDS))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("buy");
        assertThatThrownBy(() -> PreIpoRevenue.of(PreIpoEntry.Kind.BUYBACK, BigDecimal.ONE, new BigDecimal("110"), new BigDecimal("100"), GST, true, TDS))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("buyback");
    }
}
