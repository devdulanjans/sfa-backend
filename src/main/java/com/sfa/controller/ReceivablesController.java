package com.sfa.controller;

import com.sfa.dto.InvoicePaymentDto;
import com.sfa.dto.invoice.InvoiceSummaryDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Invoice;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.InvoiceRepository;
import com.sfa.security.TenantGuard;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.InvoicePaymentService;
import com.sfa.service.InvoicePaymentService.CustomerPaymentRequest;
import com.sfa.service.InvoicePaymentService.CustomerPaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Customer balances for the business owner (V120): who owes what, how much is overdue, a
 * customer's open invoices and payments, and receiving a payment that settles their oldest
 * invoices first. Part of SFA invoicing — the Finance module's receivables screen is the
 * accountant's view of the same ledger. Channel-scoped: invoices are tenant-filtered.
 */
@RestController
@RequestMapping("/api/receivables")
@RequiredArgsConstructor
@Tag(name = "Receivables")
@SecurityRequirement(name = "bearerAuth")
@RequiresLicense(LicensedPackage.SFA)
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
public class ReceivablesController {

    private static final List<Invoice.InvoiceStatus> OUTSTANDING =
            List.of(Invoice.InvoiceStatus.ISSUED, Invoice.InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final InvoicePaymentService paymentService;

    public record CustomerBalance(UUID customerId, String customerName, String customerCode, String phone,
                                  long openInvoices, BigDecimal balance, BigDecimal overdue, LocalDate oldestDue) {}

    public record Totals(BigDecimal balance, BigDecimal overdue, long customers, long openInvoices) {}

    public record Balances(Totals totals, List<CustomerBalance> customers) {}

    public record Statement(UUID customerId, String customerName, String customerCode, String phone,
                            BigDecimal creditLimit, Integer creditDays,
                            BigDecimal balance, BigDecimal overdue,
                            List<InvoiceSummaryDto> openInvoices, List<InvoicePaymentDto> payments) {}

    @GetMapping("/customers")
    @Transactional(readOnly = true)
    @Operation(summary = "Outstanding balance per customer, with the overdue part")
    public Balances balances() {
        List<CustomerBalance> rows = invoiceRepo.balancesByCustomer(OUTSTANDING, LocalDate.now()).stream()
                .map(r -> new CustomerBalance((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                        ((Number) r[4]).longValue(), (BigDecimal) r[5], (BigDecimal) r[6], (LocalDate) r[7]))
                .toList();
        Totals totals = new Totals(
                rows.stream().map(CustomerBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(CustomerBalance::overdue).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.size(),
                rows.stream().mapToLong(CustomerBalance::openInvoices).sum());
        return new Balances(totals, rows);
    }

    @GetMapping("/customers/{customerId}")
    @Transactional(readOnly = true)
    @Operation(summary = "A customer's open invoices and payment history")
    public Statement statement(@PathVariable UUID customerId) {
        Customer c = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        TenantGuard.requireVisible(c, "Customer", customerId);
        List<InvoiceSummaryDto> open = invoiceRepo.findPayableForCustomer(customerId, OUTSTANDING).stream()
                .map(InvoiceSummaryDto::from).toList();
        BigDecimal balance = open.stream().map(InvoiceSummaryDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = open.stream().filter(InvoiceSummaryDto::overdue)
                .map(InvoiceSummaryDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Statement(c.getId(), c.getName(), c.getCustomerCode(), c.getPhone(),
                c.getCreditLimit(), c.getCreditDays(), balance, overdue, open,
                paymentService.listForCustomer(customerId));
    }

    @PostMapping("/customers/{customerId}/payments")
    @Operation(summary = "Receive a payment from a customer — settles their unpaid invoices, oldest due first")
    public CustomerPaymentResult receive(@PathVariable UUID customerId, @RequestBody CustomerPaymentRequest request,
                                         @AuthenticationPrincipal UserDetailsImpl user) {
        return paymentService.receiveFromCustomer(customerId, request, user.getId());
    }
}
