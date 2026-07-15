package com.sfa.service;

import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UpdateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Distributor;
import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final DistributorRepository  distributorRepository;
    private final CustomerRepository     customerRepository;
    private final PasswordEncoder        passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserDto> list(UUID distributorId, Pageable pageable) {
        if (distributorId != null) {
            return userRepository.findByDistributorId(distributorId, pageable).map(UserDto::from);
        }
        return userRepository.findAll(pageable).map(UserDto::from);
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

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User user = findOrThrow(id);

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

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto toggleStatus(UUID id) {
        User user = findOrThrow(id);
        user.setStatus(user.getStatus() == User.UserStatus.ACTIVE
                ? User.UserStatus.INACTIVE
                : User.UserStatus.ACTIVE);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto changeRole(UUID id, UUID roleId) {
        User user = findOrThrow(id);
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

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
