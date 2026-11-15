package com.sfa.security;

import com.sfa.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * The signed-in session's role and links. With memberships (V119) one account can be a sales
 * rep in one channel and a customer in another, so "what role is this person acting as" must
 * come from the session's membership, not from the account row ({@code users.role_id}).
 * Services that receive only a userId use these helpers when that id is the caller's own.
 */
public final class CurrentSession {

    private CurrentSession() {}

    public static Optional<UserDetailsImpl> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserDetailsImpl impl ? Optional.of(impl) : Optional.empty();
    }

    /** The role {@code user} is acting as: the session's role when it's the caller, else the account's. */
    public static String roleOf(User user) {
        return principal().filter(p -> p.getId().equals(user.getId()))
                .map(UserDetailsImpl::getRoleName)
                .orElse(user.getRole().getName());
    }

    /** The customer {@code user} orders as: the session membership's customer when it's the caller. */
    public static UUID linkedCustomerIdOf(User user) {
        return principal().filter(p -> p.getId().equals(user.getId()))
                .map(UserDetailsImpl::getLinkedCustomerId)
                .orElse(user.getCustomer() != null ? user.getCustomer().getId() : null);
    }
}
