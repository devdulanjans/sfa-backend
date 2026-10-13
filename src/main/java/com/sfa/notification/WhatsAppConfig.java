package com.sfa.notification;

/** Resolved WhatsApp Cloud API account for one send — a channel's own, or the server default. */
public record WhatsAppConfig(String phoneNumberId, String accessToken, String templateName, String templateLanguage) {

    public boolean isComplete() {
        return phoneNumberId != null && !phoneNumberId.isBlank() && accessToken != null && !accessToken.isBlank();
    }
}
