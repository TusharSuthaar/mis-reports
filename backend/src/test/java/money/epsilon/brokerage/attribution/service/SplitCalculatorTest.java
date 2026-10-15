package money.epsilon.brokerage.attribution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import money.epsilon.brokerage.attribution.service.SplitCalculator.NegativeNet;
import money.epsilon.brokerage.attribution.service.SplitCalculator.Part;
import money.epsilon.brokerage.attribution.service.SplitCalculator.Share;
import money.epsilon.brokerage.partner.domain.PartnerType;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    private static final List<PartnerType> ORDER = List.of(PartnerType.SUB_BROKER, PartnerType.REFERRAL, PartnerType.POSP);

    private static Share sb(String pct) {
        return new Share(PartnerType.SUB_BROKER, 1, "SB", new BigDecimal(pct));
    }

    private static Share ref(String pct) {
        return new Share(PartnerType.REFERRAL, 2, "REF", new BigDecimal(pct));
    }

    private static List<String> amounts(List<Part> parts) {
        return parts.stream().map(p -> p.amount().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    void subBrokerFirstThenReferralOnWhatIsLeftThenEpsilon() {
        List<Part> parts = SplitCalculator.split(new BigDecimal("100.0000"), List.of(ref("0.2"), sb("0.5")), ORDER, NegativeNet.SAME_PERCENT);
        assertThat(amounts(parts)).containsExactly("50", "10", "40");
        assertThat(parts.get(1).base()).isEqualByComparingTo("50");
        assertThat(parts.get(2).isCompany()).isTrue();
    }

    @Test
    void theUsualCaseSubBrokerAtZeroAndReferralPaid() {
        List<Part> parts = SplitCalculator.split(new BigDecimal("100"), List.of(sb("0"), ref("0.5")), ORDER, NegativeNet.SAME_PERCENT);
        assertThat(amounts(parts)).containsExactly("0", "50", "50");
    }

    @Test
    void noPartnerMeansEpsilonKeepsEverything() {
        List<Part> parts = SplitCalculator.split(new BigDecimal("80.6417"), List.of(), ORDER, NegativeNet.SAME_PERCENT);
        assertThat(parts).singleElement().satisfies(p -> {
            assertThat(p.isCompany()).isTrue();
            assertThat(p.amount()).isEqualByComparingTo("80.6417");
        });
    }

    @Test
    void partnersAreRoundedToPaiseAndEpsilonTakesTheRemainderSoItAlwaysAddsUp() {
        List<Part> parts = SplitCalculator.split(new BigDecimal("10.5555"), List.of(sb("0.333333"), ref("0.5")), ORDER, NegativeNet.SAME_PERCENT);
        assertThat(amounts(parts)).containsExactly("3.52", "3.52", "3.5155");
        assertThat(parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10.5555");
    }

    @Test
    void negativeNetFollowsTheSetting() {
        assertThat(amounts(SplitCalculator.split(new BigDecimal("-100"), List.of(sb("0.5")), ORDER, NegativeNet.SAME_PERCENT)))
                .containsExactly("-50", "-50");
        assertThat(amounts(SplitCalculator.split(new BigDecimal("-100"), List.of(sb("0.5")), ORDER, NegativeNet.EPSILON_ABSORBS)))
                .containsExactly("0", "-100");
    }

    @Test
    void aRoleMissingFromTheConfiguredOrderIsAnError() {
        assertThatThrownBy(() -> SplitCalculator.split(BigDecimal.TEN, List.of(ref("0.5")), List.of(PartnerType.SUB_BROKER), NegativeNet.SAME_PERCENT))
                .isInstanceOf(IllegalStateException.class);
    }
}
