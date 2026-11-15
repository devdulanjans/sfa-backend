package com.sfa.dto.membership;

import com.sfa.entity.Membership;
import com.sfa.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * One workspace of an account (V119): a role in one business. Used for the workspace picker,
 * invitations, and a business's list of members.
 */
public record MembershipDto(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String role,
        UUID customerId,
        String customerName,
        String status,
        Instant invitedAt,
        Instant lastUsedAt,
        // Person details — filled for a business's member list, so owners see who holds a role.
        UUID userId,
        String username,
        String fullName,
        String email
) {
    public static MembershipDto of(Membership m) {
        return of(m, false);
    }

    public static MembershipDto of(Membership m, boolean withPerson) {
        User u = withPerson ? m.getUser() : null;
        return new MembershipDto(
                m.getId(), m.getTenant().getId(), m.getTenant().getCode(), m.getTenant().getName(),
                m.getRole().getName(),
                m.getCustomer() != null ? m.getCustomer().getId() : null,
                m.getCustomer() != null ? m.getCustomer().getName() : null,
                m.getStatus().name(), m.getInvitedAt(), m.getLastUsedAt(),
                u != null ? u.getId() : null, u != null ? u.getUsername() : null,
                u != null ? u.getFullName() : null, u != null ? u.getEmail() : null);
    }
}
