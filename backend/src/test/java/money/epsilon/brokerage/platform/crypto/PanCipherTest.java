package money.epsilon.brokerage.platform.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class PanCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final PanCipher cipher = new PanCipher(KEY_A, KEY_B);

    @Test
    void encryptsAndDecryptsWithoutLeakingThePan() {
        String stored = cipher.encrypt("ABCDE1234F");
        assertThat(stored).startsWith("v1:").doesNotContain("ABCDE1234F");
        assertThat(cipher.decrypt(stored)).isEqualTo("ABCDE1234F");
    }

    @Test
    void samePanEncryptsDifferentlyEachTimeButHashesTheSame() {
        assertThat(cipher.encrypt("ABCDE1234F")).isNotEqualTo(cipher.encrypt("ABCDE1234F"));
        assertThat(cipher.hash("ABCDE1234F")).isEqualTo(cipher.hash("ABCDE1234F")).hasSize(64);
        assertThat(cipher.hash("ABCDE1234F")).isNotEqualTo(cipher.hash("ABCDE1234G"));
    }

    @Test
    void hashDependsOnTheKey() {
        PanCipher other = new PanCipher(KEY_A, KEY_A);
        assertThat(other.hash("ABCDE1234F")).isNotEqualTo(cipher.hash("ABCDE1234F"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String stored = cipher.encrypt("ABCDE1234F");
        byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
        bytes[bytes.length / 2] ^= 1; // flip one bit of real data
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalisesAndValidates() {
        assertThat(PanCipher.normalise(" abcde1234f ")).isEqualTo("ABCDE1234F");
        assertThat(PanCipher.normalise("  ")).isNull();
        assertThatThrownBy(() -> PanCipher.normalise("ABCD1234F")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masksTheMiddle() {
        assertThat(PanCipher.mask("ABCDE1234F")).isEqualTo("ABCDE****F");
        assertThat(PanCipher.mask(null)).isNull();
        assertThat(PanCipher.mask("ABC1234")).isEqualTo("*******"); // too short to be a PAN
    }

    @Test
    void refusesMissingOrShortKeys() {
        assertThatThrownBy(() -> new PanCipher("", KEY_B)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PanCipher(Base64.getEncoder().encodeToString(new byte[16]), KEY_B))
                .hasMessageContaining("32 bytes");
    }
}
