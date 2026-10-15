package money.epsilon.brokerage.platform.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is acting right now. Background work and start-up tasks act as "system". */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) return Optional.of(p);
        return Optional.empty();
    }

    public static String username() {
        return principal().map(AppUserPrincipal::getUsername).orElse(SYSTEM);
    }
}
