package money.epsilon.brokerage.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First thing on every request: gives it a reference (or keeps a sane one from a proxy), returns it in the
 * {@code X-Request-Id} header, and writes one access line when it ends. The query string is never logged: it
 * can hold a PAN (client search).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger access = LoggerFactory.getLogger("access");
    private static final Pattern SANE_ID = Pattern.compile("[A-Za-z0-9-]{8,40}");
    private static final long SLOW_MS = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestContext.HEADER);
        String id = incoming != null && SANE_ID.matcher(incoming).matches() ? incoming : RequestContext.newReference();
        MDC.put(RequestContext.REQUEST_ID, id);
        response.setHeader(RequestContext.HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            String path = request.getRequestURI();
            // Reads that worked are routine (the job screen polls every second): keep them out of the INFO log.
            boolean routine = "GET".equals(request.getMethod()) && status < 400 && ms < SLOW_MS;
            if (path.startsWith("/api/") && !routine) {
                access.info("{} {} -> {} in {} ms", request.getMethod(), path, status, ms);
            } else if (access.isDebugEnabled()) {
                access.debug("{} {} -> {} in {} ms", request.getMethod(), path, status, ms);
            }
            MDC.clear();
        }
    }
}
