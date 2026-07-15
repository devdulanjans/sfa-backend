package com.sfa.security;

import com.sfa.entity.Customer;
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

public class UserDetailsImpl implements UserDetails {

    @Getter private final UUID        id;
    @Getter private final String      roleName;
    @Getter private final UUID        linkedCustomerId;
    @Getter private final Set<UUID>   assignedCustomerIds;
    private final String   username;
    private final String   password;
    private final boolean  active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(User user) {
        this.id          = user.getId();
        this.username    = user.getUsername();
        this.password    = user.getPasswordHash();
        this.roleName    = user.getRole().getName();
        this.active      = user.getStatus() == User.UserStatus.ACTIVE;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        this.linkedCustomerId = user.getCustomer() != null ? user.getCustomer().getId() : null;
        // Lazy load is safe here — UserDetailsServiceImpl.loadUserByUsername is @Transactional
        this.assignedCustomerIds = user.getAssignedCustomers().stream()
                .map(Customer::getId)
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
