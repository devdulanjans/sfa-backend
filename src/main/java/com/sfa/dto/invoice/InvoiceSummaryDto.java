package com.sfa.dto.invoice;

import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryDto(
        UUID       id,
        String     invoiceNumber,
        CustomerRef customer,
        LocalDate  issuedDate,
        LocalDate  dueDate,
        BigDecimal total,
        Integer    printCount,
        String     status,
        String     orderNumber,
        Instant    orderDate,
        String     salesRepName,
        BigDecimal paidAmount,
        BigDecimal balance,
        boolean    overdue,
        UUID       orderId,
        /** Order partly invoiced (V120) — this invoice is one of several. */
        String     orderStatus
) {
    public record CustomerRef(UUID id, String name, String phone, String email) {}

    public static InvoiceSummaryDto from(Invoice i) {
        Order order   = i.getOrder();
        User  salesRep = order != null ? order.getSalesRep() : null;
        return new InvoiceSummaryDto(
                i.getId(),
                i.getInvoiceNumber(),
                i.getCustomer() != null
                        ? new CustomerRef(
                                i.getCustomer().getId(),
                                i.getCustomer().getName(),
                                i.getCustomer().getPhone(),
                                i.getCustomer().getEmail())
                        : null,
                i.getIssuedDate(),
                i.getDueDate(),
                i.getTotal(),
                i.getPrintCount(),
                i.getStatus() != null ? i.getStatus().name() : null,
                order != null ? order.getOrderNumber() : null,
                order != null ? order.getOrderDate() : null,
                salesRep != null
                        ? (salesRep.getFullName() != null ? salesRep.getFullName() : salesRep.getUsername())
                        : null,
                i.getPaidAmount(),
                i.getBalance(),
                i.isOverdue(),
                order != null ? order.getId() : null,
                order != null && order.getStatus() != null ? order.getStatus().name() : null
        );
    }
}
