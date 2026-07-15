package com.sfa.repository;

import com.sfa.entity.LicenseSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LicenseSettingsRepository extends JpaRepository<LicenseSettings, UUID> {

    Optional<LicenseSettings> findFirstByOrderByUpdatedAtDesc();
}
