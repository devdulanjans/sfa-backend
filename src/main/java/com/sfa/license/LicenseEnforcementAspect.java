package com.sfa.license;

import com.sfa.exception.LicenseDeniedException;
import com.sfa.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Enforces {@link RequiresLicense} on top of (not instead of) normal @PreAuthorize role
 * checks — this fires for every caller regardless of role, including SUPER_ADMIN of the
 * install it's running on, since a license restricts the install, not a person.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class LicenseEnforcementAspect {

    private final LicenseService licenseService;

    @Before("@within(com.sfa.license.RequiresLicense) || @annotation(com.sfa.license.RequiresLicense)")
    public void checkLicense(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RequiresLicense annotation = method.getAnnotation(RequiresLicense.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequiresLicense.class);
        }
        if (annotation == null) {
            return;
        }

        boolean enabled = switch (annotation.value()) {
            case SFA -> licenseService.isSfaEnabled();
            case POS -> licenseService.isPosEnabled();
        };
        if (!enabled) {
            throw new LicenseDeniedException(annotation.value());
        }
    }
}
