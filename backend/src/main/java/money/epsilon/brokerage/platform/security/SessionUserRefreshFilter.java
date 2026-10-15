package money.epsilon.brokerage.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import money.epsilon.brokerage.platform.logging.RequestContext;
import money.epsilon.brokerage.platform.user.AppUser;
import money.epsilon.brokerage.platform.user.AppUserRepository;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-reads the signed-in user on every request, so a deactivation or role change takes effect immediately
 * instead of when the session expires.
 */
class SessionUserRefreshFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final SecurityContextRepository contextRepository;

    SessionUserRefreshFilter(AppUserRepository users, SecurityContextRepository contextRepository) {
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AppUserPrincipal> current = CurrentUser.principal();
        if (current.isPresent()) {
            Optional<AppUser> user = users.findById(current.get().id());
            if (user.isEmpty() || !user.get().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
            } else if (!current.get().sameAccessAs(user.get())) {
                AppUserPrincipal fresh = new AppUserPrincipal(user.get());
                fresh.eraseCredentials();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, request, response);
            }
        }
        // Every log line of this request names who made it.
        MDC.put(RequestContext.USER, CurrentUser.username());
        chain.doFilter(request, response);
    }
}
