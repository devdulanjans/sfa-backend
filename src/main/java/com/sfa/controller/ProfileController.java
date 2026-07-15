package com.sfa.controller;

import com.sfa.dto.user.ChangePasswordRequest;
import com.sfa.dto.user.UpdateProfileRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.UserRepository;
import com.sfa.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public UserDto getProfile(@AuthenticationPrincipal UserDetailsImpl principal) {
        return UserDto.from(findUser(principal.getId()));
    }

    @PatchMapping
    @Transactional
    public UserDto updateProfile(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody UpdateProfileRequest req) {

        User user = findUser(principal.getId());

        if (!user.getEmail().equalsIgnoreCase(req.email())
                && userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email is already in use");
        }

        user.setFullName(req.fullName());
        user.setEmail(req.email());
        return UserDto.from(userRepository.save(user));
    }

    @PatchMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody ChangePasswordRequest req) {

        User user = findUser(principal.getId());

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private User findUser(java.util.UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
