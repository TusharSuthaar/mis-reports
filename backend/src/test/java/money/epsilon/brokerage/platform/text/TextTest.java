package money.epsilon.brokerage.platform.text;

import static org.assertj.core.api.Assertions.assertThat;

import money.epsilon.brokerage.platform.crypto.PanCipher;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void keysForMatchingNamesHeadersAndPans() {
        assertThat(Text.key("  Alpha\u00a0 wealth ")).isEqualTo("ALPHA WEALTH");
        assertThat(Text.nameKey("A. K.  Shah")).isEqualTo(Text.nameKey("a k shah"));
        assertThat(Text.compact("Sub-Broker %")).isEqualTo("SUBBROKER");
        assertThat(Text.compact(null)).isEmpty();
        assertThat(PanCipher.normaliseOrNull(" abcde1234f ")).isEqualTo("ABCDE1234F");
        assertThat(PanCipher.normaliseOrNull("ABC123")).isNull();
        assertThat(PanCipher.normaliseOrNull("")).isNull();
    }
}
