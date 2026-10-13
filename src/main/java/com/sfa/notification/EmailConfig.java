package com.sfa.notification;

import com.sfa.entity.ChannelNotificationSettings.SmtpSecurity;

/** Resolved SMTP account for one send — a channel's own, or the server default. */
public record EmailConfig(String host, int port, String username, String password, SmtpSecurity security,
                          String fromEmail, String fromName) {

    public boolean isComplete() {
        return host != null && !host.isBlank() && fromEmail != null && !fromEmail.isBlank();
    }
}
