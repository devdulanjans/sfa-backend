package com.sfa.exception;

import com.sfa.license.LicensedPackage;

public class LicenseDeniedException extends RuntimeException {

    private final LicensedPackage licensedPackage;

    public LicenseDeniedException(LicensedPackage licensedPackage) {
        super("This feature is not included in your current license.");
        this.licensedPackage = licensedPackage;
    }

    public LicensedPackage getLicensedPackage() {
        return licensedPackage;
    }
}
