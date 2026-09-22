package com.sfa.config;

import com.sfa.security.TenantFilterInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Must run after Spring Boot's OpenEntityManagerInViewInterceptor (default order 0)
        // has bound the request's EntityManager — otherwise unwrap(Session.class) resolves
        // a throwaway, non-transactional Session and enableFilter() silently has no effect.
        registry.addInterceptor(tenantFilterInterceptor).order(100);
    }
}
