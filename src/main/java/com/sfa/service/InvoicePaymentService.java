package com.sfa.service;

import com.sfa.dto.InvoicePaymentDto;
import com.sfa.dto.RecordInvoicePaymentRequest;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.Customer;
import com.sfa.entity.Invoice;
import com.sfa.entity.InvoicePayment;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.PaymentMethod;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.InvoicePaymentRepository;
import com.sfa.repository.InvoiceRepository;
import com.sfa.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Recording a payment here is the exact mechanism behind "customer pays an invoice ->
 * account, bank balance, and financial reports update automatically": it posts a real
 * Dr Bank / Cr Accounts Receivable journal entry, so the bank account's live balance and
 * every ledger-derived report immediately reflect it — no separate step required.
 *
 * <p>V120: payments are part of invoicing, not only of the Finance module — a channel owner
 * records part and balance payments too. A bank account is optional: without one the money
 * is held on Cash on Hand (1050). A customer's payment can be settled across their unpaid
 * invoices, oldest due first, under one receipt number; a payment recorded by mistake is
 * voided (its entry reversed), never deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoicePaymentService {

    private static final String ACCOUNTS_RECEIVABLE_CODE = "1100";
    private static final String CASH_ON_HAND_CODE        = "1050";

    private static final List<Invoice.InvoiceStatus> PAYABLE =
            List.of(Invoice.InvoiceStatus.ISSUED, Invoice.InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;
    private final CustomerRepository customerRepo;
    private final AuditLogService auditLog;

    @PersistenceContext
    private EntityManager em;

    /** A customer's payment, settled across their unpaid invoices. */
    public record CustomerPaymentRequest(
            BigDecimal amount,
            LocalDate paymentDate,
            String paymentMethod,
            String referenceNumber,
            UUID bankAccountId,
            String notes,
            /** Only these invoices (still oldest due first); null/empty = any unpaid invoice. */
            List<UUID> invoiceIds) {}

    public record CustomerPaymentResult(String receiptNumber, BigDecimal amount, List<InvoicePaymentDto> allocations,
                                        BigDecimal remainingBalance) {}

    public InvoicePaymentDto recordPayment(UUID invoiceId, RecordInvoicePaymentRequest req, UUID createdBy) {
        Invoice invoice = lockedInvoice(invoiceId);
        BigDecimal amount = req.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(invoice.getBalance()) > 0) {
            throw new BusinessException("Payment amount exceeds outstanding balance of " + invoice.getBalance());
        }
        Funds funds = funds(req.bankAccountId());
        InvoicePayment payment = apply(invoice, amount, funds, parseMethod(req.paymentMethod()),
                paymentDate(req.paymentDate()), req.referenceNumber(), req.notes(), nextReceiptNumber(), createdBy);
        return InvoicePaymentDto.from(payment);
    }

    /**
     * Receives one payment from a customer and settles it across their unpaid invoices, the
     * oldest due first (each invoice gets its own ledger entry; all share the receipt number).
     * More than they owe is refused — there's no customer credit balance to hold it.
     */
    public CustomerPaymentResult receiveFromCustomer(UUID customerId, CustomerPaymentRequest req, UUID createdBy) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        TenantGuard.requireVisible(customer, "Customer", customerId);

        BigDecimal amount = req.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        Set<UUID> only = req.invoiceIds() == null || req.invoiceIds().isEmpty() ? null : new HashSet<>(req.invoiceIds());
        List<Invoice> payable = new ArrayList<>();
        for (Invoice i : invoiceRepo.findPayableForCustomer(customerId, PAYABLE)) {
            if (only == null || only.contains(i.getId())) payable.add(i);
        }
        BigDecimal owed = payable.stream().map(Invoice::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (owed.signum() == 0) {
            throw new BusinessException(customer.getName() + " has no unpaid invoices");
        }
        if (amount.compareTo(owed) > 0) {
            throw new BusinessException("Payment of " + amount + " is more than the " + owed
                    + " " + customer.getName() + " owes" + (only != null ? " on the selected invoices" : ""));
        }

        Funds funds = funds(req.bankAccountId());
        PaymentMethod method = parseMethod(req.paymentMethod());
        LocalDate date = paymentDate(req.paymentDate());
        String receipt = nextReceiptNumber();

        List<InvoicePaymentDto> allocations = new ArrayList<>();
        BigDecimal left = amount;
        for (Invoice candidate : payable) {
            if (left.signum() <= 0) break;
            Invoice invoice = lockedInvoice(candidate.getId());
            BigDecimal part = invoice.getBalance().min(left);
            if (part.signum() <= 0) continue;
            allocations.add(InvoicePaymentDto.from(apply(invoice, part, funds, method, date,
                    req.referenceNumber(), req.notes(), receipt, createdBy)));
            left = left.subtract(part);
        }
        return new CustomerPaymentResult(receipt, amount, allocations, owed.subtract(amount));
    }

    /** Voids a payment recorded by mistake: reverses its entry and puts the amount back on the invoice. */
    public InvoicePaymentDto voidPayment(UUID paymentId, String reason, UUID userId) {
        InvoicePayment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        Invoice invoice = lockedInvoice(payment.getInvoice().getId());
        if (payment.isVoided()) {
            throw new BusinessException("This payment is already void");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Say why the payment is being voided");
        }
        if (payment.getJournalEntry() != null
                && payment.getJournalEntry().getStatus() == JournalEntry.JournalStatus.POSTED) {
            journalEntryService.voidEntry(payment.getJournalEntry().getId(), userId);
        }
        payment.setVoidedAt(Instant.now());
        payment.setVoidedBy(userId);
        payment.setVoidReason(reason.trim());
        paymentRepo.save(payment);

        BigDecimal paid = invoice.getPaidAmount().subtract(payment.getAmount());
        invoice.setPaidAmount(paid.signum() < 0 ? BigDecimal.ZERO : paid);
        invoice.setStatus(invoice.getPaidAmount().signum() > 0
                ? Invoice.InvoiceStatus.PARTIALLY_PAID : Invoice.InvoiceStatus.ISSUED);
        invoiceRepo.save(invoice);

        auditLog.log(userId, "VOID", "INVOICE_PAYMENT", payment.getId(), null,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "amount", payment.getAmount(), "reason", reason.trim()));
        return InvoicePaymentDto.from(payment);
    }

    @Transactional(readOnly = true)
    public List<InvoicePaymentDto> listForInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        TenantGuard.requireVisible(invoice, "Invoice", invoiceId);
        return paymentRepo.findByInvoiceIdOrderByPaymentDateDesc(invoiceId).stream()
                .map(InvoicePaymentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoicePaymentDto> listForCustomer(UUID customerId) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        TenantGuard.requireVisible(customer, "Customer", customerId);
        return paymentRepo.findByCustomer(customerId).stream().map(InvoicePaymentDto::from).toList();
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /** Where the money went: a bank account's ledger account, or Cash on Hand. */
    private record Funds(BankAccount bankAccount, ChartOfAccount account) {}

    private Funds funds(UUID bankAccountId) {
        if (bankAccountId != null) {
            BankAccount bank = bankAccountRepo.findById(bankAccountId)
                    .orElseThrow(() -> new BusinessException("Bank account not found: " + bankAccountId));
            return new Funds(bank, bank.getGlAccount());
        }
        return new Funds(null, accountRepo.findByAccountCode(CASH_ON_HAND_CODE)
                .orElseThrow(() -> new BusinessException("Cash on Hand system account is missing")));
    }

    private InvoicePayment apply(Invoice invoice, BigDecimal amount, Funds funds, PaymentMethod method,
                                 LocalDate date, String reference, String notes, String receipt, UUID createdBy) {
        ChartOfAccount accountsReceivable = accountRepo.findByAccountCode(ACCOUNTS_RECEIVABLE_CODE)
                .orElseThrow(() -> new BusinessException("Accounts Receivable system account is missing"));

        JournalEntry entry = journalEntryService.postEntry(
                date,
                "Payment for invoice " + invoice.getInvoiceNumber() + " — " + invoice.getCustomer().getName(),
                List.of(
                        new JournalEntryService.LinePosting(funds.account().getId(), amount, null, invoice.getInvoiceNumber()),
                        new JournalEntryService.LinePosting(accountsReceivable.getId(), null, amount, invoice.getInvoiceNumber())
                ),
                JournalEntry.SourceType.INVOICE_PAYMENT,
                invoice.getId(),
                createdBy);

        InvoicePayment payment = paymentRepo.save(InvoicePayment.builder()
                .invoice(invoice)
                .bankAccount(funds.bankAccount())
                .amount(amount)
                .paymentDate(date)
                .paymentMethod(method)
                .referenceNumber(blank(reference))
                .notes(blank(notes))
                .receiptNumber(receipt)
                .journalEntry(entry)
                .createdBy(createdBy)
                .build());

        BigDecimal newPaidAmount = invoice.getPaidAmount().add(amount);
        invoice.setPaidAmount(newPaidAmount);
        invoice.setStatus(newPaidAmount.compareTo(invoice.getTotal()) >= 0
                ? Invoice.InvoiceStatus.PAID
                : Invoice.InvoiceStatus.PARTIALLY_PAID);
        invoiceRepo.save(invoice);

        auditLog.log(createdBy, "PAYMENT", "INVOICE", invoice.getId(), null,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "amount", amount,
                       "receiptNumber", receipt, "balance", invoice.getBalance()));
        return payment;
    }

    /** Loads (channel-checked) and row-locks an invoice, so two payments can't both take its balance. */
    private Invoice lockedInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        TenantGuard.requireVisible(invoice, "Invoice", invoiceId);
        em.flush();
        em.refresh(invoice, LockModeType.PESSIMISTIC_WRITE);
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw new BusinessException("Invoice " + invoice.getInvoiceNumber() + " is cancelled");
        }
        return invoice;
    }

    private String nextReceiptNumber() {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('payment_receipt_seq')").getSingleResult()).longValue();
        return "RCP-%02d-%06d".formatted(LocalDate.now().getYear() % 100, seq);
    }

    private static LocalDate paymentDate(LocalDate d) {
        LocalDate date = d != null ? d : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("Payment date can't be in the future");
        }
        return date;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()) return PaymentMethod.CASH;
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + method);
        }
    }
}
