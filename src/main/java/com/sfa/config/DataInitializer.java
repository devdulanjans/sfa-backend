package com.sfa.config;

import com.sfa.entity.User;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_USERNAME = "superadmin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    private final UserRepository    userRepo;
    private final RoleRepository    roleRepo;
    private final PasswordEncoder   passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        roleRepo.findByName("SUPER_ADMIN").ifPresentOrElse(role -> {
            var existing = userRepo.findByUsername(ADMIN_USERNAME);

            if (existing.isEmpty()) {
                User admin = User.builder()
                        .username(ADMIN_USERNAME)
                        .email("admin@sfasystem.com")
                        .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(role)
                        .fullName("System Administrator")
                        .status(User.UserStatus.ACTIVE)
                        .build();
                userRepo.save(admin);
                log.info("DataInitializer: superadmin user created");

            } else if (!passwordEncoder.matches(ADMIN_PASSWORD, existing.get().getPasswordHash())) {
                User admin = existing.get();
                admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
                userRepo.save(admin);
                log.info("DataInitializer: superadmin password hash corrected");

            } else {
                log.info("DataInitializer: superadmin already exists with correct credentials");
            }

        }, () -> log.warn("DataInitializer: SUPER_ADMIN role not found — check Flyway migrations"));
    }
}
