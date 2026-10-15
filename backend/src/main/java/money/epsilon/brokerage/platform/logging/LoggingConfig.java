package money.epsilon.brokerage.platform.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Request context on every API call; scheduling for housekeeping (error-log retention). */
@Configuration
@EnableScheduling
class LoggingConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LogContextInterceptor()).addPathPatterns("/api/**");
    }
}
