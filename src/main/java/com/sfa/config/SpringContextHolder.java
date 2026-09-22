package com.sfa.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Lets plain (non-Spring-managed) JPA entity listeners reach Spring beans —
 * needed by TenantAwareEntityListener, which Hibernate instantiates via
 * reflection rather than the Spring container.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("Spring context not yet initialized");
        }
        return context.getBean(type);
    }
}
