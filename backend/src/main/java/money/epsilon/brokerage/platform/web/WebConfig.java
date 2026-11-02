package money.epsilon.brokerage.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Request checks shared by every module's endpoints. */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final ProductListFilterCheck productListFilters;

    WebConfig(ProductListFilterCheck productListFilters) {
        this.productListFilters = productListFilters;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(productListFilters).addPathPatterns("/api/**");
    }
}
