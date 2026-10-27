package money.epsilon.brokerage.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.BusinessType;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Category;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Details;
import money.epsilon.brokerage.insurance.domain.InsurancePolicy.Figures;
import money.epsilon.brokerage.insurance.service.InsuranceSheetText;
import org.junit.jupiter.api.Test;

/** R-95: an insurance policy's expected and POSP commissions, as the policy sheet works them out. */
class InsuranceCommissionTest {

    private static Details policy(String net, String expectedPct, String expectedComm, Long posp, String share, String pospComm) {
        return new Details(LocalDate.of(2026, 8, 1), 1L, posp, "INSURER", Category.MOTOR, null, "PRODUCT", "P1", BusinessType.NEW, null, null,
                null, null, null, new BigDecimal(net), new BigDecimal(net), expectedPct == null ? null : new BigDecimal(expectedPct),
                expectedComm == null ? null : new BigDecimal(expectedComm), null, null, share == null ? null : new BigDecimal(share),
                pospComm == null ? null : new BigDecimal(pospComm), null);
    }

    @Test
    void expectedIsNetTimesPercentAndThePospGetsExpectedPercentTimesTheAgreedShare() {
        Figures f = InsurancePolicy.figures(policy("12345.67", "17.5", null, 9L, "40", null));
        assertThat(f.expectedComm()).isEqualByComparingTo("2160.49");   // 12,345.67 × 17.5%
        assertThat(f.pospPercent()).isEqualByComparingTo("7");          // 17.5% × 40%
        assertThat(f.pospComm()).isEqualByComparingTo("864.20");        // 12,345.67 × 7%
        assertThat(f.expectedAsGiven()).isFalse();
    }

    @Test
    void typedAmountsStandInsteadOfTheCalculation() {
        Figures f = InsurancePolicy.figures(policy("10000", "15", "1400", 9L, "50", "600"));
        assertThat(f.expectedComm()).isEqualByComparingTo("1400");
        assertThat(f.pospComm()).isEqualByComparingTo("600");
        assertThat(f.expectedAsGiven()).isTrue();
        assertThat(f.pospAsGiven()).isTrue();
    }

    @Test
    void withNoPospNothingIsShared() {
        Figures f = InsurancePolicy.figures(policy("10000", "15", null, null, "50", null));
        assertThat(f.pospComm()).isEqualByComparingTo("0");
        assertThat(f.pospPercent()).isNull();
    }

    @Test
    void theSheetsFractionsMonthsAndCodesAreRead() {
        assertThat(InsuranceSheetText.percent(new BigDecimal("0.155"))).isEqualByComparingTo("15.5");
        assertThat(InsuranceSheetText.percent(new BigDecimal("1"))).isEqualByComparingTo("100");
        assertThat(InsuranceSheetText.percent(new BigDecimal("18"))).isEqualByComparingTo("18");   // already in percent
        assertThat(InsuranceSheetText.monthOf("Aug'26")).isEqualTo(YearMonth.of(2026, 8));
        assertThat(InsuranceSheetText.monthOf("September 2026")).isEqualTo(YearMonth.of(2026, 9));
        assertThat(InsuranceSheetText.category("Motor")).isEqualTo(Category.MOTOR);
        assertThat(InsuranceSheetText.businessType("Endorsement")).isEqualTo(BusinessType.ENDORSEMENT);
        assertThat(InsuranceSheetText.businessType("Portability")).isEqualTo(BusinessType.PORT);
        assertThat(InsuranceSheetText.category("Travel")).isNull();
    }
}
