package com.sfa.repository;

import com.sfa.entity.ChannelNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChannelNotificationSettingsRepository extends JpaRepository<ChannelNotificationSettings, UUID> {

    Optional<ChannelNotificationSettings> findByTenant_Id(UUID tenantId);
}
