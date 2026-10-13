package com.sfa.notification;

import com.sfa.entity.Customer;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Detached snapshot of an order or invoice — built inside a transaction, then handed to the
 * notifiers so no lazy entity is touched while an SMTP / WhatsApp call is in flight.
 */
public record SalesDocument(
        Type type,
        String number,
        LocalDate date,
        LocalDate dueDate,
        String orderNumber,
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    public enum Type {
        ORDER("Order"),
        INVOICE("Invoice");

        public final String label;

        Type(String label) {
            this.label = label;
        }
    }

    public record Line(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal, boolean free) {}

    public static SalesDocument of(Order order) {
        Customer c = order.getCustomer();
        return new SalesDocument(Type.ORDER, order.getOrderNumber(),
                order.getOrderDate() != null ? order.getOrderDate().atZone(ZONE).toLocalDate() : LocalDate.now(ZONE),
                null, null,
                c.getName(), c.getEmail(), c.getWhatsappNumber(),
                lines(order),
                order.getSubtotal(), order.getDiscountAmount(), order.getTaxAmount(), order.getTotal());
    }

    public static SalesDocument of(Invoice invoice) {
        Customer c = invoice.getCustomer();
        Order order = invoice.getOrder();
        return new SalesDocument(Type.INVOICE, invoice.getInvoiceNumber(),
                invoice.getIssuedDate(), invoice.getDueDate(), order.getOrderNumber(),
                c.getName(), c.getEmail(), c.getWhatsappNumber(),
                lines(order),
                invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getTaxTotal(), invoice.getTotal());
    }

    private static List<Line> lines(Order order) {
        return order.getItems().stream()
                .map(SalesDocument::line)
                .toList();
    }

    private static Line line(OrderItem i) {
        return new Line(i.getProduct().getName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal(),
                "FREE_PRODUCT".equals(i.getPriceSource()));
    }

    /** "Rs. 12,500.00" — same currency label the printed invoice uses. */
    public static String money(BigDecimal v) {
        return "Rs. " + String.format("%,.2f", v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }

    public static String qty(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
