package com.sfa.license;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (class-level) or a single endpoint (method-level) as requiring
 * this installation's SFA or POS package to be licensed. Enforced by
 * {@link LicenseEnforcementAspect} on top of (not instead of) normal {@code @PreAuthorize}
 * role checks — it applies to every role, including SUPER_ADMIN of that install.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLicense {
    LicensedPackage value();
}
