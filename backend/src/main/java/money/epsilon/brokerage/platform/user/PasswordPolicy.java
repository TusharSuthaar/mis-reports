package money.epsilon.brokerage.platform.user;

import java.nio.charset.StandardCharsets;

/** Minimum password rules. BCrypt ignores everything past 72 bytes, so longer passwords are refused. */
final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws IllegalArgumentException with a user-readable reason */
    static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_BYTES + " bytes");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be only spaces");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw new IllegalArgumentException("Password must not contain the username");
        }
    }
}
