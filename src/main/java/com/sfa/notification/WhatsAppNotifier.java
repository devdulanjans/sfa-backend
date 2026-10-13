package com.sfa.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Meta WhatsApp Cloud API sender. Business-initiated messages must use a pre-approved template,
 * so the content goes in as the body parameters of {@code app.whatsapp.template-name}:
 * <pre>
 * {{1}} customer name    {{2}} company name   {{3}} "order" / "invoice"
 * {{4}} document number  {{5}} date           {{6}} items (single line)   {{7}} total
 * </pre>
 */
@Component
public class WhatsAppNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    /** Meta rejects parameters over 1024 chars; stay well under it, leaving room for "and N more". */
    private static final int MAX_ITEMS_TEXT = 700;

    private final WhatsAppConfig serverDefault;
    private final String apiVersion;
    private final String defaultCountryCode;
    private final RestClient http;

    public WhatsAppNotifier(@Value("${app.whatsapp.access-token:}") String accessToken,
                            @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                            @Value("${app.whatsapp.api-version:v21.0}") String apiVersion,
                            @Value("${app.whatsapp.template-name:sales_document_summary}") String templateName,
                            @Value("${app.whatsapp.template-language:en}") String templateLanguage,
                            @Value("${app.whatsapp.default-country-code:94}") String defaultCountryCode) {
        this.serverDefault = new WhatsAppConfig(phoneNumberId, accessToken, templateName, templateLanguage);
        this.apiVersion = apiVersion;
        this.defaultCountryCode = defaultCountryCode;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** The WHATSAPP_* env-var account, used by a channel that leaves its phone-number id blank. */
    public WhatsAppConfig serverDefault() {
        return serverDefault;
    }

    public void send(WhatsAppConfig cfg, SalesDocument doc, String companyName, String number) {
        String to = normalize(number);
        if (to == null) {
            throw new IllegalArgumentException("Invalid WhatsApp number: " + number);
        }
        List<Map<String, String>> params = List.of(
                text(doc.customerName()),
                text(companyName == null || companyName.isBlank() ? "us" : companyName),
                text(doc.type().label.toLowerCase()),
                text(doc.number()),
                text(DATE.format(doc.date())),
                text(itemsSummary(doc)),
                text(SalesDocument.money(doc.total())));

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", firstNonBlank(cfg.templateName(), serverDefault.templateName()),
                        "language", Map.of("code", firstNonBlank(cfg.templateLanguage(), serverDefault.templateLanguage())),
                        "components", List.of(Map.of("type", "body", "parameters", params))));
        try {
            http.post()
                    .uri("https://graph.facebook.com/{v}/{id}/messages", apiVersion, cfg.phoneNumberId())
                    .header("Authorization", "Bearer " + cfg.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Meta's error body names the real cause (unapproved template, param count, bad number...).
            throw new IllegalStateException("WhatsApp API " + ex.getStatusCode().value() + ": "
                    + ex.getResponseBodyAsString(), ex);
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** "Ice Tube 5kg x 10, Ice Block x 2 (free)" — template params may not contain newlines. */
    static String itemsSummary(SalesDocument doc) {
        StringBuilder sb = new StringBuilder();
        List<SalesDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SalesDocument.Line l = lines.get(i);
            String part = clean(l.productName()) + " x " + SalesDocument.qty(l.quantity()) + (l.free() ? " (free)" : "");
            if (sb.length() + part.length() > MAX_ITEMS_TEXT) {
                sb.append(" and ").append(lines.size() - i).append(" more");
                break;
            }
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(part);
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    /**
     * Digits-only international format Meta expects: "0771234567" -> "94771234567",
     * "+94 77 123 4567" / "0094771234567" -> "94771234567". Null when it can't be a phone number.
     */
    String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        boolean international = s.startsWith("+") || s.startsWith("00");
        String digits = s.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (!international) {
            if (digits.startsWith("0")) digits = defaultCountryCode + digits.substring(1);
            else if (digits.length() <= 9) digits = defaultCountryCode + digits;
        }
        return digits.length() >= 8 && digits.length() <= 15 ? digits : null;
    }

    private static Map<String, String> text(String value) {
        return Map.of("type", "text", "text", clean(value));
    }

    /** Template params reject newlines, tabs and runs of more than four spaces. */
    private static String clean(String s) {
        return s == null || s.isBlank() ? "-" : s.replaceAll("\\s+", " ").trim();
    }
}
