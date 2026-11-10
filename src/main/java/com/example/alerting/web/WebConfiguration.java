package com.example.alerting.web;

import com.example.alerting.identity.IdentityResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Plugs the identity handling into Spring MVC: every {@code /api/v1} request is identified, and controller
 * methods can take a {@code CurrentUser} parameter.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final IdentityResolver identityResolver;

    /**
     * Creates the configuration.
     *
     * @param identityResolver identifies callers; today the demo header, later real authentication
     */
    public WebConfiguration(IdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdentityInterceptor(identityResolver)).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
