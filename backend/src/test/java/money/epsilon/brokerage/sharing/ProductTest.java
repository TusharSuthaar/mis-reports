package money.epsilon.brokerage.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import money.epsilon.brokerage.sharing.domain.Product;
import org.junit.jupiter.api.Test;

/** R-97: Pre-IPO is shown as "Unlisted", and either name (or the code) finds it. */
class ProductTest {

    @Test
    void preIpoIsShownAsUnlistedAndFoundByEitherName() {
        assertThat(Product.PREIPO.label()).isEqualTo("Unlisted");
        assertThat(Product.PREIPO.code()).isEqualTo("PREIPO");
        for (String name : new String[] {"Pre-IPO", "Pre IPO", "pre ipo", "PREIPO", "Unlisted", "UNLISTED"}) {
            assertThat(Product.find(name)).as(name).contains(Product.PREIPO);
        }
        assertThat(Product.FD.label()).isEqualTo("FD");
        assertThat(Product.FD.names()).containsExactly("FD");
        assertThat(Product.find("Bonds")).contains(Product.BOND);
        assertThat(Product.find("Unknown")).isEmpty();
    }
}
