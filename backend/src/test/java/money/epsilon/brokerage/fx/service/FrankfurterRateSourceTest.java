package money.epsilon.brokerage.fx.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** R-84: reading Frankfurter's answer. */
class FrankfurterRateSourceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheRupeeRateAndTheDayItIsFrom() throws Exception {
        var fetched = FrankfurterRateSource.parse(mapper.readTree("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-25\",\"rates\":{\"INR\":83.1234}}"));
        assertThat(fetched).isPresent();
        assertThat(fetched.get().rate()).isEqualByComparingTo("83.1234");
        assertThat(fetched.get().rateDate()).hasToString("2026-09-25");
        assertThat(FrankfurterRateSource.parse(mapper.readTree("{\"message\":\"not found\"}"))).isEmpty();
    }
}
