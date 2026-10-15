package money.epsilon.brokerage.platform.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Names the module and controller method handling the request, so every log line says where it came from. */
class LogContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            Class<?> type = method.getBeanType();
            MDC.put(RequestContext.MODULE, RequestContext.moduleOf(type));
            MDC.put(RequestContext.ACTION, type.getSimpleName() + "." + method.getMethod().getName());
        }
        return true;
    }
}
