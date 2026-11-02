package money.epsilon.brokerage.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuses a filter a {@link ProductListFilters} endpoint does not know (L-07). */
@Component
class ProductListFilterCheck implements HandlerInterceptor {

    /** The shared product-list filters, as the screens send them (frontend {@code FILTER_KEYS}). */
    static final List<String> SHARED = List.of("from", "to", "q", "rmId", "partnerId", "platform", "platformId", "lender", "fund", "amc",
            "pop", "payment", "includeCancelled");

    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(ProductListFilters.class)) return true;
        Set<String> known = new LinkedHashSet<>(SHARED);
        for (MethodParameter p : method.getMethodParameters()) {
            p.initParameterNameDiscovery(NAMES);
            RequestParam rp = p.getParameterAnnotation(RequestParam.class);
            if (rp != null) known.add(!rp.name().isEmpty() ? rp.name() : !rp.value().isEmpty() ? rp.value() : p.getParameterName());
        }
        Set<String> unknown = new TreeSet<>(request.getParameterMap().keySet());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new ValidationException("Unknown filter " + String.join(", ", unknown.stream().map(u -> "'" + u + "'").toList())
                    + ". This list takes " + String.join(", ", known) + " (months as from / to, e.g. from=2026-08&to=2026-08).");
        }
        return true;
    }

}
