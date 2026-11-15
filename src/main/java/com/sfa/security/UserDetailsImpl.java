package com.sfa.security;

import com.sfa.entity.Customer;
import com.sfa.entity.Membership;
import com.sfa.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserDetailsImpl implements UserDetails {

    @Getter private final UUID        id;
    /** The membership this session runs as (V119) - null for platform accounts and legacy tokens. */
    @Getter private final UUID        membershipId;
    @Getter private final String      roleName;
    @Getter private final UUID        linkedCustomerId;
    @Getter private final UUID        linkedDistributorId;
    @Getter private final Set<UUID>   assignedCustomerIds;
    private final String   username;
    private final String   password;
    private final boolean  active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(User user) {
        this(user, null);
    }

    /**
     * @param membership the role this session acts as (V119). Its role, customer and distributor
     *                   replace the account-level ones, so one account can be a sales rep here
     *                   and a customer elsewhere. Null means the account's own (platform roles,
     *                   or the password check during login).
     */
    public UserDetailsImpl(User user, Membership membership) {
        this(user, membership, null);
    }

    /**
     * @param roleOverride replaces the role when there's no membership — AppScope.NO_ROLE for an
     *                     app session not acting as any business role, so the account's legacy
     *                     role (users.role_id) can never leak into it.
     */
    public UserDetailsImpl(User user, Membership membership, String roleOverride) {
        this.id          = user.getId();
        this.membershipId = membership != null ? membership.getId() : null;
        this.username    = user.getUsername();
        this.password    = user.getPasswordHash();
        this.roleName    = membership != null ? membership.getRole().getName()
                : roleOverride != null ? roleOverride : user.getRole().getName();
        this.active      = user.getStatus() == User.UserStatus.ACTIVE;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        if (membership != null) {
            this.linkedCustomerId    = membership.getCustomer() != null ? membership.getCustomer().getId() : null;
            this.linkedDistributorId = membership.getLoginDistributor() != null ? membership.getLoginDistributor().getId() : null;
        } else {
            this.linkedCustomerId    = user.getCustomer() != null ? user.getCustomer().getId() : null;
            this.linkedDistributorId = user.getLoginDistributor() != null ? user.getLoginDistributor().getId() : null;
        }
        // Lazy load is safe here — UserDetailsServiceImpl.loadUserByUsername is @Transactional.
        // Effective scope is direct assignedCustomers UNION every assigned CustomerGroup's
        // members — still empty overall means "all customers" (unchanged from before groups
        // existed). Mirrors how Customer.effectiveAssignedProductIds unions direct + group
        // product assignments.
        Stream<UUID> direct = user.getAssignedCustomers().stream().map(Customer::getId);
        Stream<UUID> viaGroups = user.getCustomerGroups().stream()
                .flatMap(g -> g.getMembers().stream())
                .map(Customer::getId);
        this.assignedCustomerIds = Stream.concat(direct, viaGroups)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String  getPassword()  { return password; }
    @Override public String  getUsername()  { return username; }
    @Override public boolean isEnabled()    { return active; }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
}
