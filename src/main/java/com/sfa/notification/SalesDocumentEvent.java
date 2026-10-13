package com.sfa.notification;

import java.util.UUID;

/**
 * Published inside the creating transaction; {@link CustomerNotificationListener} picks it up
 * only after commit, so a rolled-back order/invoice never reaches the customer.
 */
public record SalesDocumentEvent(SalesDocument.Type type, UUID id) {

    public static SalesDocumentEvent orderSubmitted(UUID orderId) {
        return new SalesDocumentEvent(SalesDocument.Type.ORDER, orderId);
    }

    public static SalesDocumentEvent invoiceGenerated(UUID invoiceId) {
        return new SalesDocumentEvent(SalesDocument.Type.INVOICE, invoiceId);
    }
}
