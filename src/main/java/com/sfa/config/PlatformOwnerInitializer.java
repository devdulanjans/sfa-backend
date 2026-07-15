package com.sfa.config;

import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets the 'platformowner' account's password from an env var on boot, rather than
 * shipping a known password in a migration (V49) that goes out to every client install.
 * Until PLATFORM_OWNER_PASSWORD is set, the account stays locked — its seeded hash
 * matches no known plaintext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformOwnerInitializer implements CommandLineRunner {

    private static final String OWNER_USERNAME = "platformowner";

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.platform-owner.bootstrap-password:}")
    private String bootstrapPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("PlatformOwnerInitializer: PLATFORM_OWNER_PASSWORD not set — "
                    + "'platformowner' account remains locked until it is provided.");
            return;
        }
        userRepo.findByUsername(OWNER_USERNAME).ifPresentOrElse(owner -> {
            if (!passwordEncoder.matches(bootstrapPassword, owner.getPasswordHash())) {
                owner.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                userRepo.save(owner);
                log.info("PlatformOwnerInitializer: platformowner password set from PLATFORM_OWNER_PASSWORD");
            }
        }, () -> log.warn("PlatformOwnerInitializer: 'platformowner' user not found — check Flyway migrations"));
    }
}
