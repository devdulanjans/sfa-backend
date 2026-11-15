package com.sfa.service;

import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * Self sign-up on the public website (V119): a personal account with no business role. Any
 * business can then invite it — as owner, sales rep, customer… — and the person accepts.
 */
@Service
@RequiredArgsConstructor
public class AccountSignupService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLog;

    /** @return the new account's username (they can also sign in with their email or phone) */
    @Transactional
    public String signUp(String fullName, String email, String phone, String password) {
        String mail = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        String tel = phone == null || phone.isBlank() ? null : ShopService.normalizePhone(phone);
        if (mail == null && tel == null) {
            throw new BusinessException("Enter an email address or a mobile number.");
        }
        if (phone != null && !phone.isBlank() && tel == null) {
            throw new BusinessException("Enter a mobile number like 077 123 4567.");
        }
        // Same message either way, so the form can't be used to probe who has an account.
        if ((mail != null && userRepository.existsByEmailIgnoreCase(mail)) || (tel != null && userRepository.existsByPhone(tel))) {
            throw new BusinessException("An account already exists for this email or mobile number. Sign in instead, or reset your password.");
        }
        Role accountRole = roleRepository.findByName("ACCOUNT")
                .orElseThrow(() -> new BusinessException("Sign-up isn't available yet."));

        User u = new User();
        u.setUsername(uniqueUsername(mail != null ? mail.substring(0, mail.indexOf('@')) : tel));
        // users.email is required and unique: phone-only accounts get an internal placeholder.
        u.setEmail(mail != null ? mail : tel + "@phone.avlis.local");
        u.setPhone(tel);
        u.setFullName(fullName.trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(accountRole);
        u.setStatus(User.UserStatus.ACTIVE);
        User saved = userRepository.save(u);
        auditLog.log(saved.getId(), "SIGN_UP", "USER", saved.getId(), null, Map.of("username", saved.getUsername()));
        return saved.getUsername();
    }

    private String uniqueUsername(String seed) {
        String base = seed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "");
        if (base.length() < 3) base = (base + "user").substring(0, Math.max(3, base.length()));
        if (base.length() > 45) base = base.substring(0, 45);
        String name = base;
        for (int n = 2; userRepository.existsByUsername(name); n++) name = base + n;
        return name;
    }
}
