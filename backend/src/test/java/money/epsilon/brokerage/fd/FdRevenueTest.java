package money.epsilon.brokerage.fd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import money.epsilon.brokerage.fd.domain.FdRevenue;
import org.junit.jupiter.api.Test;

class FdRevenueTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    void grossTdsGstNetAndInvoice() {
        FdRevenue r = FdRevenue.of(d("1000000"), d("1"), true, d("2"), d("18"));
        assertThat(r.gross()).isEqualByComparingTo("10000.00");
        assertThat(r.tds()).isEqualByComparingTo("200.00");
        assertThat(r.gst()).isEqualByComparingTo("1800.00");
        assertThat(r.net()).isEqualByComparingTo("9800.00");
        assertThat(r.invoice()).isEqualByComparingTo("11800.00");
    }

    @Test
    void withoutTdsNetIsGrossAndFiguresRoundToPaise() {
        FdRevenue r = FdRevenue.of(d("123457"), d("0.75"), false, d("10"), d("18"));
        assertThat(r.gross()).isEqualByComparingTo("925.93");   // 925.9275
        assertThat(r.tds()).isEqualByComparingTo("0.00");
        assertThat(r.gst()).isEqualByComparingTo("166.67");     // 166.6674
        assertThat(r.net()).isEqualByComparingTo("925.93");
        assertThat(r.invoice()).isEqualByComparingTo("1092.60");
    }
}
