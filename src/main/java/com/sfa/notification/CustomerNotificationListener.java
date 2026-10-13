package com.sfa.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the automatic customer notification once the order/invoice transaction has committed,
 * off the request thread so a slow SMTP or WhatsApp call never delays the API response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNotificationListener {

    private final CustomerNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSalesDocument(SalesDocumentEvent event) {
        try {
            notificationService.notifyAutomatically(event);
        } catch (Exception ex) {
            log.warn("Customer notification for {} {} failed: {}", event.type(), event.id(), ex.getMessage());
        }
    }
}
