package money.epsilon.brokerage.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import money.epsilon.brokerage.loan.domain.LoanRevenue;
import org.junit.jupiter.api.Test;

/** R-80, with figures like the LOANS register's. */
class LoanRevenueTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void payoutPercentOfTheAmountWithTdsAndGst() {
        // 3,60,661 × 1.5% = 5,409.92; TDS 2% = 108.20; GST 18% = 973.79
        LoanRevenue r = LoanRevenue.of(d("360661"), d("1.5"), null, true, d("2"), d("18"));
        assertThat(r.gross()).isEqualByComparingTo("5409.92");
        assertThat(r.tds()).isEqualByComparingTo("108.20");
        assertThat(r.gst()).isEqualByComparingTo("973.79");
        assertThat(r.net()).isEqualByComparingTo("5301.72");
        assertThat(r.invoice()).isEqualByComparingTo("6383.71");
    }

    @Test
    void aFixedAmountIsAddedAndNoPayoutYetIsZero() {
        LoanRevenue withFixed = LoanRevenue.of(d("360661"), d("1.5"), d("3"), true, d("2"), d("18"));
        assertThat(withFixed.gross()).isEqualByComparingTo("5412.92");
        LoanRevenue onlyFixed = LoanRevenue.of(d("4500000"), null, d("13500"), true, d("5"), d("18"));
        assertThat(onlyFixed.gross()).isEqualByComparingTo("13500");
        assertThat(onlyFixed.tds()).isEqualByComparingTo("675");
        assertThat(onlyFixed.invoice()).isEqualByComparingTo("15930");
        LoanRevenue none = LoanRevenue.of(d("1200000"), null, null, true, d("2"), d("18"));
        assertThat(none.gross()).isEqualByComparingTo("0");
        assertThat(none.invoice()).isEqualByComparingTo("0");
    }
}
