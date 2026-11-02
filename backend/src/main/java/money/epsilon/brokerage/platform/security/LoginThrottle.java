package money.epsilon.brokerage.platform.security;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import money.epsilon.brokerage.platform.audit.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limits password guessing (H-02, R-101). Per user: after {@value #DELAY_AFTER} wrong passwords in a row each further try
 * waits longer (1, 2, 4, 8, 16 minutes); at {@value #LOCK_AFTER} the account is locked until an administrator unlocks it.
 * Per address: {@value #ADDRESS_LIMIT} failures in 15 minutes pause sign-in from it for 15 minutes, whatever the usernames.
 * A refused attempt is not checked at all, so it says nothing about the password. Every failure and lock is audited.
 */
@Component
class LoginThrottle {

    static final int DELAY_AFTER = 5;
    static final int LOCK_AFTER = 10;
    static final int ADDRESS_LIMIT = 20;
    static final Duration ADDRESS_WINDOW = Duration.ofMinutes(15);

    private final JdbcClient jdbc;
    private final AuditService audit;

    LoginThrottle(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private record UserState(long id, int failed, Instant lockedUntil, boolean locked) {
    }

    private Optional<UserState> user(String username) {
        return jdbc.sql("SELECT id, failed_logins, locked_until, locked FROM app_user WHERE lower(username) = lower(?)")
                .param(username.trim())
                .query((rs, n) -> new UserState(rs.getLong(1), rs.getInt(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(), rs.getBoolean(4)))
                .optional();
    }

    /** Why this attempt is refused before the password is looked at, or empty to go ahead. */
    @Transactional(readOnly = true)
    public Optional<String> refusal(String username, String address) {
        Instant now = Instant.now();
        long recent = jdbc.sql("SELECT count(*) FROM login_failure WHERE ip = ? AND at > ?")
                .params(address, java.sql.Timestamp.from(now.minus(ADDRESS_WINDOW))).query(Long.class).single();
        if (recent >= ADDRESS_LIMIT) {
            return Optional.of("Too many failed sign-ins from this computer. Try again in " + ADDRESS_WINDOW.toMinutes() + " minutes.");
        }
        Optional<UserState> u = user(username);
        if (u.isEmpty()) return Optional.empty();
        if (u.get().locked()) {
            return Optional.of("This account is locked after " + LOCK_AFTER + " wrong passwords. Ask an administrator to unlock it.");
        }
        Instant until = u.get().lockedUntil();
        if (until != null && until.isAfter(now)) {
            long minutes = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
            return Optional.of("Too many wrong passwords. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes."));
        }
        return Optional.empty();
    }

    /** A wrong password (or unknown username): counted for the address and, when the user exists, for the user. */
    @Transactional
    public void failed(String username, String address) {
        String name = username.trim();
        jdbc.sql("DELETE FROM login_failure WHERE at < now() - interval '1 day'").update();
        jdbc.sql("INSERT INTO login_failure (username, ip) VALUES (?, ?)").params(name.length() > 100 ? name.substring(0, 100) : name, address).update();
        Optional<UserState> u = user(name);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("username", name);
        after.put("address", address);
        if (u.isEmpty()) {
            audit.recordAs(name, "USER", null, "SIGN_IN_FAILED", null, after);
            return;
        }
        int failed = u.get().failed() + 1;
        boolean lock = failed >= LOCK_AFTER;
        Instant until = !lock && failed >= DELAY_AFTER ? Instant.now().plus(Duration.ofMinutes(1L << (failed - DELAY_AFTER))) : null;
        jdbc.sql("UPDATE app_user SET failed_logins = ?, locked = ?, locked_until = ? WHERE id = ?")
                .params(failed, lock, until == null ? null : java.sql.Timestamp.from(until), u.get().id()).update();
        after.put("failedInARow", failed);
        audit.recordAs(name, "USER", u.get().id(), "SIGN_IN_FAILED", null, after);
        if (lock) audit.recordAs(name, "USER", u.get().id(), "SIGN_IN_LOCKED", null, after);
    }

    /** A correct password: the user's count starts again. */
    @Transactional
    public void succeeded(long userId, String username, String address) {
        jdbc.sql("UPDATE app_user SET failed_logins = 0, locked_until = NULL WHERE id = ?").param(userId).update();
        audit.recordAs(username, "USER", userId, "SIGN_IN", null, Map.of("address", address));
    }
}
