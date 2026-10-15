package money.epsilon.brokerage.platform.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsAReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.check("Correct-Horse-9", "ramesh")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.check("Short-1", "ramesh"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 10");
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> PasswordPolicy.check("RAMESH-2026-ok", "ramesh"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("username");
    }

    @Test
    void rejectsPasswordLongerThanBcryptAccepts() {
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(73), "ramesh"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("72 bytes");
        // multi-byte characters count by bytes, not characters
        assertThatThrownBy(() -> PasswordPolicy.check("₹".repeat(25), "ramesh"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("72 bytes");
    }

    @Test
    void rejectsAllSpaces() {
        assertThatThrownBy(() -> PasswordPolicy.check(" ".repeat(12), "ramesh")).isInstanceOf(IllegalArgumentException.class);
    }
}
