package com.sfa.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin/super-admin setting a NEW password for someone else's account — no current
 *  password needed (the admin doesn't and shouldn't know it); authority comes entirely
 *  from the caller's own role/channel scope, enforced the same way as every other
 *  admin-on-another-user action (see UserService.rejectOutOfScopeTarget). */
public record AdminResetPasswordRequest(
        @NotBlank @Size(min = 8) String newPassword,
        /** Defaults to true when omitted — an admin-initiated reset should normally force
         *  the account to pick its own new password at next login rather than continuing
         *  to use one the admin now knows. */
        Boolean forceChangeOnNextLogin
) {}
