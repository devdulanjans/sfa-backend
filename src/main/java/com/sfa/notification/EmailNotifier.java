package com.sfa.notification;

import com.sfa.dto.CompanyProfileDto;
import com.sfa.entity.ChannelNotificationSettings.SmtpSecurity;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * SMTP sender for order/invoice summaries. Each channel can bring its own mailbox
 * ({@link EmailConfig}); {@link #serverDefault()} is the SMTP_* env-var account used by a channel
 * that leaves its SMTP host blank. Deliberately not Spring's mail auto-configuration, so an install
 * without server-level SMTP gets no mail health check dragging /actuator/health DOWN.
 */
@Component
public class EmailNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String CELL = "padding:8px;border:1px solid #e5e7eb;";

    private final EmailConfig serverDefault;

    public EmailNotifier(@Value("${app.mail.host:}") String host,
                         @Value("${app.mail.port:587}") int port,
                         @Value("${app.mail.username:}") String username,
                         @Value("${app.mail.password:}") String password,
                         @Value("${app.mail.starttls:true}") boolean starttls,
                         @Value("${app.mail.ssl:false}") boolean ssl,
                         @Value("${app.mail.from:}") String from,
                         @Value("${app.mail.from-name:}") String fromName) {
        this.serverDefault = new EmailConfig(host, port, username, password,
                ssl ? SmtpSecurity.SSL : starttls ? SmtpSecurity.STARTTLS : SmtpSecurity.NONE,
                !from.isBlank() ? from : username, fromName);
    }

    public EmailConfig serverDefault() {
        return serverDefault;
    }

    public void send(EmailConfig cfg, SalesDocument doc, CompanyProfileDto company, String to) throws Exception {
        String companyName = company != null && company.companyName() != null ? company.companyName() : "";
        JavaMailSenderImpl sender = sender(cfg);
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        String displayName = notBlank(cfg.fromName()) ? cfg.fromName() : companyName;
        h.setFrom(displayName.isBlank()
                ? new InternetAddress(cfg.fromEmail())
                : new InternetAddress(cfg.fromEmail(), displayName, "UTF-8"));
        h.setTo(to);
        if (company != null && notBlank(company.email())) {
            h.setReplyTo(company.email());
        }
        h.setSubject("%s %s%s".formatted(doc.type().label, doc.number(),
                companyName.isBlank() ? "" : " from " + companyName));
        h.setText(plainText(doc, companyName), html(doc, company, companyName));
        sender.send(msg);
    }

    private static JavaMailSenderImpl sender(EmailConfig cfg) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(cfg.host());
        s.setPort(cfg.port() > 0 ? cfg.port() : (cfg.security() == SmtpSecurity.SSL ? 465 : 587));
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean auth = notBlank(cfg.username());
        if (auth) {
            s.setUsername(cfg.username());
            s.setPassword(cfg.password());
        }
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(auth));
        p.put("mail.smtp.starttls.enable", String.valueOf(cfg.security() == SmtpSecurity.STARTTLS));
        p.put("mail.smtp.starttls.required", String.valueOf(cfg.security() == SmtpSecurity.STARTTLS));
        p.put("mail.smtp.ssl.enable", String.valueOf(cfg.security() == SmtpSecurity.SSL));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        return s;
    }

    private static String plainText(SalesDocument doc, String companyName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(doc.customerName()).append(",\n\n");
        sb.append("Your ").append(doc.type().label.toLowerCase()).append(' ').append(doc.number())
          .append(" dated ").append(DATE.format(doc.date())).append(" has been created.\n\n");
        for (SalesDocument.Line l : doc.lines()) {
            sb.append("- ").append(l.productName()).append(" x ").append(SalesDocument.qty(l.quantity()))
              .append(l.free() ? " (FREE)" : " = " + SalesDocument.money(l.lineTotal())).append('\n');
        }
        sb.append("\nTotal: ").append(SalesDocument.money(doc.total())).append('\n');
        if (doc.dueDate() != null) sb.append("Due date: ").append(DATE.format(doc.dueDate())).append('\n');
        sb.append("\nThank you,\n").append(companyName);
        return sb.toString();
    }

    private static String html(SalesDocument doc, CompanyProfileDto company, String companyName) {
        StringBuilder rows = new StringBuilder();
        int n = 1;
        for (SalesDocument.Line l : doc.lines()) {
            rows.append("<tr>")
                .append(td(String.valueOf(n++), "center"))
                .append(td(esc(l.productName()) + (l.free() ? " <b style=\"color:#15803d\">(FREE)</b>" : ""), "left"))
                .append(td(SalesDocument.qty(l.quantity()), "right"))
                .append(td(l.free() ? "-" : SalesDocument.money(l.unitPrice()), "right"))
                .append(td(l.free() ? "-" : SalesDocument.money(l.lineTotal()), "right"))
                .append("</tr>");
        }

        StringBuilder meta = new StringBuilder();
        meta.append(metaRow(doc.type().label + " No", doc.number()));
        meta.append(metaRow("Date", DATE.format(doc.date())));
        if (doc.orderNumber() != null) meta.append(metaRow("Order No", doc.orderNumber()));
        if (doc.dueDate() != null) meta.append(metaRow("Due Date", DATE.format(doc.dueDate())));

        StringBuilder totals = new StringBuilder();
        totals.append(totalRow("Subtotal", doc.subtotal(), false));
        if (isPositive(doc.discount())) totals.append(totalRow("Discount", doc.discount().negate(), false));
        if (isPositive(doc.tax())) totals.append(totalRow("Tax", doc.tax(), false));
        totals.append(totalRow("Total", doc.total(), true));

        StringBuilder contact = new StringBuilder();
        if (company != null) {
            if (notBlank(company.phone())) contact.append("Tel: ").append(esc(company.phone())).append("<br>");
            if (notBlank(company.email())) contact.append("Email: ").append(esc(company.email()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:640px;margin:0 auto\">");
        sb.append("<h2 style=\"margin:0 0 4px\">").append(esc(companyName)).append("</h2>");
        sb.append("<p style=\"margin:0 0 16px;color:#6b7280\">").append(contact).append("</p>");
        sb.append("<p>Dear ").append(esc(doc.customerName())).append(",</p>");
        sb.append("<p>Your ").append(doc.type().label.toLowerCase()).append(" <b>").append(esc(doc.number()))
          .append("</b> has been created. The details are below.</p>");
        sb.append("<table style=\"border-collapse:collapse;margin-bottom:16px;font-size:14px\">").append(meta).append("</table>");
        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:14px\"><thead><tr style=\"background:#f3f4f6\">")
          .append(th("#", "center")).append(th("Item", "left")).append(th("Qty", "right"))
          .append(th("Unit Price", "right")).append(th("Amount", "right"))
          .append("</tr></thead><tbody>").append(rows).append("</tbody></table>");
        sb.append("<table style=\"border-collapse:collapse;margin:12px 0 0 auto;font-size:14px\">").append(totals).append("</table>");
        sb.append("<p style=\"margin-top:24px\">Thank you for your business.</p>");
        sb.append("<p style=\"color:#6b7280;font-size:12px\">").append(esc(companyName)).append("</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String th(String content, String align) {
        return "<th style=\"" + CELL + "text-align:" + align + "\">" + content + "</th>";
    }

    private static String td(String content, String align) {
        return "<td style=\"" + CELL + "text-align:" + align + "\">" + content + "</td>";
    }

    private static String metaRow(String label, String value) {
        return "<tr><td style=\"padding:2px 16px 2px 0;color:#6b7280\">" + label
                + "</td><td style=\"padding:2px 0\"><b>" + esc(value) + "</b></td></tr>";
    }

    private static String totalRow(String label, BigDecimal value, boolean bold) {
        String style = bold
                ? "padding:6px 8px;font-weight:bold;font-size:16px;border-top:2px solid #1f2937"
                : "padding:4px 8px";
        return "<tr><td style=\"" + style + "\">" + label + "</td><td style=\"" + style + ";text-align:right\">"
                + SalesDocument.money(value) + "</td></tr>";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
