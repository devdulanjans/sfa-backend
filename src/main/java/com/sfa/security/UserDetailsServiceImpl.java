package com.sfa.security;

import com.sfa.entity.Membership;
import com.sfa.repository.MembershipRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepo;
    private final MembershipRepository membershipRepo;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsernameOrEmail(username)
                .map(UserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * The session principal for a token carrying a membership ("mid"). Empty when the membership
     * no longer belongs to this account or is no longer usable (revoked, or the business was
     * disabled) - the request then runs unauthenticated and gets a 401.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UserDetailsImpl> loadForMembership(String username, java.util.UUID membershipId) {
        return loadForMembership(username, membershipId, null);
    }

    /** Also refuses a membership whose role the token's app may not act as (AppScope). */
    @Transactional(readOnly = true)
    public java.util.Optional<UserDetailsImpl> loadForMembership(String username, java.util.UUID membershipId, String app) {
        return userRepo.findByUsernameOrEmail(username).flatMap(user -> membershipRepo.findById(membershipId)
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .filter(Membership::isUsable)
                .filter(m -> AppScope.allows(app, m.getRole().getName()))
                .map(m -> new UserDetailsImpl(user, m)));
    }

    /**
     * An app session with no workspace (e.g. a website visitor's own account): no business role.
     * Platform accounts (SUPER_ADMIN, PLATFORM_OWNER) keep theirs in the admin panel only.
     */
    @Transactional(readOnly = true)
    public UserDetailsImpl loadWithoutMembership(String username, String app) {
        com.sfa.entity.User user = userRepo.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        boolean platform = java.util.Set.of(com.sfa.entity.Role.SUPER_ADMIN, com.sfa.entity.Role.PLATFORM_OWNER)
                .contains(user.getRole().getName());
        if (app == null || (platform && AppScope.ADMIN.equals(app))) {
            return new UserDetailsImpl(user);
        }
        return new UserDetailsImpl(user, null, AppScope.NO_ROLE);
    }
}
