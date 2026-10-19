package money.epsilon.brokerage.bond;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import money.epsilon.brokerage.bond.domain.BondRevenue;
import org.junit.jupiter.api.Test;

class BondRevenueTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static final BigDecimal GST = d("18");

    /** Rows of the bond register kept in Excel before the platform. */
    @Test
    void matchesTheBondRegistersFigures() {
        // GST-inclusive platform paying 67% of the mark-up: 10,00,000 at 0.4% → invoice 2,680.
        BondRevenue share = BondRevenue.of(d("1000000"), d("0.4"), d("67"), true, GST, true, d("5"));
        assertThat(share.brokerage()).isEqualByComparingTo("2680.00");
        assertThat(share.gross()).isEqualByComparingTo("2271.19");
        assertThat(share.gst()).isEqualByComparingTo("408.81");
        assertThat(share.invoice()).isEqualByComparingTo("2680.00");
        assertThat(share.tds()).isEqualByComparingTo("113.56");
        assertThat(share.net()).isEqualByComparingTo("2157.63");

        // GST-inclusive, full mark-up: 10,00,000 at 0.4% → 4,000 (register: gross 3,390, GST 610.20, TDS 169.50).
        BondRevenue full = BondRevenue.of(d("1000000"), d("0.4"), d("100"), true, GST, true, d("5"));
        assertThat(full.gross()).isEqualByComparingTo("3389.83");
        assertThat(full.gst()).isEqualByComparingTo("610.17");
        assertThat(full.invoice()).isEqualByComparingTo("4000.00");
        assertThat(full.tds()).isEqualByComparingTo("169.49");

        // GST on top: 5,90,000 at 0.15% → gross 885, GST 159.30, TDS 44.25, invoice 1,044.30.
        BondRevenue onTop = BondRevenue.of(d("590000"), d("0.15"), d("100"), false, GST, true, d("5"));
        assertThat(onTop.gross()).isEqualByComparingTo("885.00");
        assertThat(onTop.gst()).isEqualByComparingTo("159.30");
        assertThat(onTop.tds()).isEqualByComparingTo("44.25");
        assertThat(onTop.net()).isEqualByComparingTo("840.75");
        assertThat(onTop.invoice()).isEqualByComparingTo("1044.30");
    }

    @Test
    void withoutTdsNetIsGross() {
        BondRevenue r = BondRevenue.of(d("1035100"), d("0.5"), d("100"), false, GST, false, d("5"));
        assertThat(r.gross()).isEqualByComparingTo("5175.50");
        assertThat(r.tds()).isEqualByComparingTo("0.00");
        assertThat(r.net()).isEqualByComparingTo("5175.50");
    }
}
