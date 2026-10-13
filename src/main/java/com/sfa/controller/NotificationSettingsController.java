package com.sfa.controller;

import com.sfa.notification.ChannelNotificationSettingsService;
import com.sfa.notification.ChannelNotificationSettingsService.SettingsDto;
import com.sfa.notification.ChannelNotificationSettingsService.UpdateRequest;
import com.sfa.notification.CustomerNotificationService;
import com.sfa.notification.CustomerNotificationService.ChannelStatus;
import com.sfa.notification.CustomerNotificationService.ShareResult;
import com.sfa.notification.NotificationChannel;
import com.sfa.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Settings > Notifications — the active channel's customer email / WhatsApp setup. */
@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
@Tag(name = "Notification Settings")
@SecurityRequirement(name = "bearerAuth")
public class NotificationSettingsController {

    public record TestRequest(@NotNull NotificationChannel channel, String to) {}

    private final ChannelNotificationSettingsService settingsService;
    private final CustomerNotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SettingsDto get() {
        return settingsService.getCurrent();
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SettingsDto update(@Valid @RequestBody UpdateRequest req,
                              @AuthenticationPrincipal UserDetailsImpl user) {
        return settingsService.update(req, user.getId());
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Send a sample invoice through the saved setup to check it works")
    public ShareResult test(@Valid @RequestBody TestRequest req) {
        return notificationService.sendTest(req.channel(), req.to());
    }

    @GetMapping("/channels")
    @PreAuthorize("isAuthenticated() and !hasAnyRole('CUSTOMER','DISTRIBUTOR')")
    @Operation(summary = "Whether email / WhatsApp sending is enabled and set up for the active channel")
    public List<ChannelStatus> channels() {
        return notificationService.channelStatus();
    }
}
