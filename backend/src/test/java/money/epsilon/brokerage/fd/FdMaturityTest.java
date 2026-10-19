package money.epsilon.brokerage.fd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import money.epsilon.brokerage.fd.domain.FdEntry.Compounding;
import money.epsilon.brokerage.fd.domain.FdEntry.InterestFrequency;
import money.epsilon.brokerage.fd.domain.FdMaturity;
import org.junit.jupiter.api.Test;

class FdMaturityTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    /** Rows of the FD register kept in Excel before the platform (made-up clients, the companies' real figures). */
    @Test
    void matchesTheFdRegistersFiguresYearlyThenSimpleForLeftOverMonths() {
        assertThat(FdMaturity.value(d("500000"), d("6.55"), 12, InterestFrequency.CUMULATIVE, null)).isEqualByComparingTo("532750.00");
        // 33 months: two years compounded, nine months simple → 6,02,504 in the register.
        assertThat(FdMaturity.value(d("500000"), d("7"), 33, InterestFrequency.CUMULATIVE, Compounding.YEARLY)).isEqualByComparingTo("602503.63");
        assertThat(FdMaturity.value(d("300000"), d("6.8"), 12, InterestFrequency.CUMULATIVE, Compounding.YEARLY)).isEqualByComparingTo("320400.00");
    }

    @Test
    void cumulativeCompoundsAsChosenAndPayoutAddsSimpleInterest() {
        BigDecimal lakh = d("100000");
        BigDecimal eight = d("8");
        assertThat(FdMaturity.value(lakh, eight, 12, InterestFrequency.CUMULATIVE, Compounding.QUARTERLY)).isEqualByComparingTo("108243.22");
        assertThat(FdMaturity.value(lakh, eight, 12, InterestFrequency.CUMULATIVE, Compounding.HALF_YEARLY)).isEqualByComparingTo("108160.00");
        // 7 months quarterly: two quarters compounded, one month simple.
        assertThat(FdMaturity.value(lakh, eight, 7, InterestFrequency.CUMULATIVE, Compounding.QUARTERLY)).isEqualByComparingTo("104733.60");
        // Interest paid out: principal plus all interest received.
        assertThat(FdMaturity.value(lakh, eight, 6, InterestFrequency.MONTHLY, null)).isEqualByComparingTo("104000.00");
    }
}
