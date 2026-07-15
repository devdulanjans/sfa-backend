package com.sfa.service;

import com.sfa.dto.auth.LoginRequest;
import com.sfa.dto.auth.LoginResponse;
import com.sfa.dto.auth.RefreshRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.JwtTokenProvider;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      jwtProvider;
    private final UserRepository        userRepo;
    private final DistributorRepository distributorRepo;
    private final UserPermissionService userPermissionService;
    private final LicenseService        licenseService;

    public LoginResponse login(LoginRequest request) {
        // Check inactive status BEFORE authenticate() so Spring Security's filter
        // cannot intercept the exception — BusinessException goes through @RestControllerAdvice cleanly.
        userRepo.findByUsername(request.username()).ifPresent(u -> {
            if (u.getStatus() == User.UserStatus.INACTIVE) {
                throw new BusinessException("Account is inactive");
            }
        });

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepo.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("User not found"));

        String accessToken  = jwtProvider.generateAccessToken(auth);
        String refreshToken = jwtProvider.generateRefreshToken(request.username());

        List<DistributorDto> distributors = distributorRepo.findByUserId(user.getId())
                .stream().map(DistributorDto::from).toList();
        List<String> permissions = resolvePermissions(user);
        UUID customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return new LoginResponse(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled());
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtProvider.validateRefreshToken(token)) {
            throw new BusinessException("Invalid or expired refresh token");
        }
        String username = jwtProvider.getUsernameFromToken(token);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String newAccessToken  = jwtProvider.generateAccessToken(auth);
        String newRefreshToken = jwtProvider.generateRefreshToken(username);

        List<DistributorDto> distributors = distributorRepo.findByUserId(user.getId())
                .stream().map(DistributorDto::from).toList();
        List<String> permissions = resolvePermissions(user);
        UUID customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled());
    }

    /**
     * SUPER_ADMIN always gets a sentinel list so the frontend knows to skip all checks.
     * Other users get their explicitly granted permissions from the DB.
     */
    private List<String> resolvePermissions(User user) {
        if ("SUPER_ADMIN".equals(user.getRole().getName())) {
            return List.of("*"); // wildcard — frontend treats this as "all permissions"
        }
        return userPermissionService.getUserPermissionKeys(user.getId());
    }

    public void logout(String accessToken, String username) {
        jwtProvider.blacklistToken(accessToken);
        jwtProvider.invalidateRefreshToken(username);
    }
}
