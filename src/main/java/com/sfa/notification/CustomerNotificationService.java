package com.sfa.notification;

import com.sfa.dto.CompanyProfileDto;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.notification.ChannelNotificationSettingsService.Resolved;
import com.sfa.repository.InvoiceRepository;
import com.sfa.repository.OrderRepository;
import com.sfa.security.TenantContext;
import com.sfa.security.TenantGuard;
import com.sfa.service.CompanyProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sends a customer the details of an order or invoice by email and/or WhatsApp, using the
 * settings of the channel the order/invoice belongs to (Settings > Notifications) — for both
 * the automatic send and the manual "Send to customer" action.
 */
@Slf4j
@Service
public class CustomerNotificationService {

    public record ShareRequest(List<NotificationChannel> channels, String email, String whatsappNumber) {}

    public record ShareResult(NotificationChannel channel, boolean sent, String recipient, String message) {}

    /** enabled = channel's toggle on; configured = an SMTP / WhatsApp account is available to it. */
    public record ChannelStatus(NotificationChannel channel, boolean enabled, boolean configured) {}

    private record Loaded(SalesDocument doc, UUID tenantId) {}

    private final OrderRepository orderRepo;
    private final InvoiceRepository invoiceRepo;
    private final ChannelNotificationSettingsService channelSettings;
    private final CompanyProfileService companyProfileService;
    private final EmailNotifier email;
    private final WhatsAppNotifier whatsApp;
    private final TransactionTemplate readTx;

    public CustomerNotificationService(OrderRepository orderRepo,
                                       InvoiceRepository invoiceRepo,
                                       ChannelNotificationSettingsService channelSettings,
                                       CompanyProfileService companyProfileService,
                                       EmailNotifier email,
                                       WhatsAppNotifier whatsApp,
                                       PlatformTransactionManager txManager) {
        this.orderRepo = orderRepo;
        this.invoiceRepo = invoiceRepo;
        this.channelSettings = channelSettings;
        this.companyProfileService = companyProfileService;
        this.email = email;
        this.whatsApp = whatsApp;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /** Status for the caller's active channel — drives the "Send to customer" buttons. */
    public List<ChannelStatus> channelStatus() {
        Resolved r = channelSettings.resolveCurrent();
        return List.of(
                new ChannelStatus(NotificationChannel.EMAIL, r.emailEnabled(), r.email() != null),
                new ChannelStatus(NotificationChannel.WHATSAPP, r.whatsappEnabled(), r.whatsapp() != null));
    }

    /** Manual send from an order screen. Runs on the request thread, so the tenant guard applies. */
    public List<ShareResult> shareOrder(UUID orderId, ShareRequest req) {
        Loaded loaded = load(() -> {
            Order o = orderRepo.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
            o = TenantGuard.requireVisible(o, "Order", orderId);
            return new Loaded(SalesDocument.of(o), tenantId(o.getTenant()));
        });
        return share(loaded, req);
    }

    /** Manual send from an invoice screen. Runs on the request thread, so the tenant guard applies. */
    public List<ShareResult> shareInvoice(UUID invoiceId, ShareRequest req) {
        Loaded loaded = load(() -> {
            Invoice i = invoiceRepo.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
            i = TenantGuard.requireVisible(i, "Invoice", invoiceId);
            return new Loaded(SalesDocument.of(i), tenantId(i.getTenant()));
        });
        return share(loaded, req);
    }

    /**
     * Automatic send after an order is submitted / an invoice is created. Called on an async
     * thread after commit, where there is no request tenant, so the lookup is by id only and
     * the channel comes from the document itself.
     */
    public void notifyAutomatically(SalesDocumentEvent event) {
        Loaded loaded = load(() -> switch (event.type()) {
            case ORDER -> orderRepo.findById(event.id())
                    .map(o -> new Loaded(SalesDocument.of(o), tenantId(o.getTenant())))
                    .orElse(null);
            case INVOICE -> invoiceRepo.findById(event.id())
                    .map(i -> new Loaded(SalesDocument.of(i), tenantId(i.getTenant())))
                    .orElse(null);
        });
        if (loaded == null) return;

        Resolved cfg = channelSettings.resolve(loaded.tenantId());
        Set<NotificationChannel> channels = usable(cfg);
        if (channels.isEmpty()) return;

        for (ShareResult r : deliver(loaded, cfg, channels, null, null)) {
            if (r.sent()) {
                log.info("{} {} sent to customer via {} ({})", loaded.doc().type().label, loaded.doc().number(),
                        r.channel(), r.recipient());
            } else {
                log.info("{} {} not sent via {}: {}", loaded.doc().type().label, loaded.doc().number(),
                        r.channel(), r.message());
            }
        }
    }

    /**
     * Settings-page test: sends a sample invoice through the active channel's saved setup to the
     * given address / number, ignoring the enabled toggle so it can be checked before switching on.
     */
    public ShareResult sendTest(NotificationChannel channel, String to) {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Enter an email address or WhatsApp number to send the test to.");
        }
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Select a channel first — notification settings are per-channel.");
        }
        Resolved cfg = channelSettings.resolve(tenantId);
        Resolved forced = new Resolved(true, cfg.email(), true, cfg.whatsapp());
        SalesDocument sample = new SalesDocument(SalesDocument.Type.INVOICE, "TEST-0001",
                LocalDate.now(ZoneId.of("Asia/Colombo")), null, "ORD-TEST-0001", "Test Customer",
                to, to,
                List.of(new SalesDocument.Line("Sample Product A", new BigDecimal("2"), new BigDecimal("500.00"),
                                new BigDecimal("1000.00"), false),
                        new SalesDocument.Line("Sample Product B", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, true)),
                new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"));
        return deliver(new Loaded(sample, tenantId), forced, List.of(channel), null, null).get(0);
    }

    private List<ShareResult> share(Loaded loaded, ShareRequest req) {
        Resolved cfg = channelSettings.resolve(loaded.tenantId());
        Collection<NotificationChannel> channels = req != null && req.channels() != null && !req.channels().isEmpty()
                ? EnumSet.copyOf(req.channels())
                : usable(cfg); // no explicit choice: every channel that can send right now
        return deliver(loaded, cfg, channels,
                req != null ? req.email() : null, req != null ? req.whatsappNumber() : null);
    }

    private List<ShareResult> deliver(Loaded loaded, Resolved cfg, Collection<NotificationChannel> channels,
                                      String emailOverride, String whatsappOverride) {
        SalesDocument doc = loaded.doc();
        CompanyProfileDto company = companyProfile(loaded.tenantId());
        String companyName = company != null ? company.companyName() : null;

        List<ShareResult> results = new ArrayList<>();
        for (NotificationChannel ch : channels) {
            String recipient = ch == NotificationChannel.EMAIL
                    ? firstNonBlank(emailOverride, doc.customerEmail())
                    : firstNonBlank(whatsappOverride, doc.customerWhatsapp());
            String problem = problem(ch, cfg, recipient);
            if (problem != null) {
                results.add(new ShareResult(ch, false, recipient, problem));
                continue;
            }
            try {
                if (ch == NotificationChannel.EMAIL) {
                    email.send(cfg.email(), doc, company, recipient.trim());
                } else {
                    whatsApp.send(cfg.whatsapp(), doc, companyName, recipient);
                }
                results.add(new ShareResult(ch, true, recipient, "Sent"));
            } catch (Exception ex) {
                log.warn("Failed to send {} {} via {} to {}: {}", doc.type().label, doc.number(), ch, recipient,
                        ex.getMessage());
                results.add(new ShareResult(ch, false, recipient, "Failed: " + ex.getMessage()));
            }
        }
        return results;
    }

    private static String problem(NotificationChannel ch, Resolved cfg, String recipient) {
        boolean isEmail = ch == NotificationChannel.EMAIL;
        if (!(isEmail ? cfg.emailEnabled() : cfg.whatsappEnabled())) {
            return (isEmail ? "Email" : "WhatsApp") + " notifications are turned off for this channel";
        }
        if ((isEmail ? cfg.email() : cfg.whatsapp()) == null) {
            return (isEmail ? "Email (SMTP)" : "WhatsApp") + " is not set up for this channel";
        }
        if (recipient == null || recipient.isBlank()) {
            return isEmail ? "Customer has no email address" : "Customer has no WhatsApp number";
        }
        return null;
    }

    private static Set<NotificationChannel> usable(Resolved cfg) {
        Set<NotificationChannel> out = EnumSet.noneOf(NotificationChannel.class);
        if (cfg.emailEnabled() && cfg.email() != null) out.add(NotificationChannel.EMAIL);
        if (cfg.whatsappEnabled() && cfg.whatsapp() != null) out.add(NotificationChannel.WHATSAPP);
        return out;
    }

    private Loaded load(Supplier<Loaded> loader) {
        return readTx.execute(status -> loader.get());
    }

    private CompanyProfileDto companyProfile(UUID tenantId) {
        try {
            return tenantId != null ? companyProfileService.getForTenant(tenantId) : null;
        } catch (Exception ex) {
            log.warn("Company profile unavailable for notification: {}", ex.getMessage());
            return null;
        }
    }

    private static UUID tenantId(Tenant t) {
        return t != null ? t.getId() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
    }
}
