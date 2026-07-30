package com.sfa.service;

import com.sfa.dto.InvoicePaymentDto;
import com.sfa.dto.RecordInvoicePaymentRequest;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.Invoice;
import com.sfa.entity.InvoicePayment;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.PaymentMethod;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.InvoicePaymentRepository;
import com.sfa.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recording a payment here is the exact mechanism behind "customer pays an invoice ->
 * account, bank balance, and financial reports update automatically": it posts a real
 * Dr Bank / Cr Accounts Receivable journal entry, so the bank account's live balance and
 * every ledger-derived report immediately reflect it — no separate step required.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoicePaymentService {

    private static final String ACCOUNTS_RECEIVABLE_CODE = "1100";

    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;

    public InvoicePaymentDto recordPayment(UUID invoiceId, RecordInvoicePaymentRequest req, UUID createdBy) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID || invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw new BusinessException("Invoice is already " + invoice.getStatus());
        }

        BigDecimal outstanding = invoice.getTotal().subtract(invoice.getPaidAmount());
        BigDecimal amount = req.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException("Payment amount exceeds outstanding balance of " + outstanding);
        }

        BankAccount bankAccount = bankAccountRepo.findById(req.bankAccountId())
                .orElseThrow(() -> new BusinessException("Bank account not found: " + req.bankAccountId()));
        ChartOfAccount accountsReceivable = accountRepo.findByAccountCode(ACCOUNTS_RECEIVABLE_CODE)
                .orElseThrow(() -> new BusinessException("Accounts Receivable system account is missing"));
        PaymentMethod paymentMethod = parseMethod(req.paymentMethod());
        LocalDate paymentDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now();

        JournalEntry entry = journalEntryService.postEntry(
                paymentDate,
                "Payment for invoice " + invoice.getInvoiceNumber() + " — " + invoice.getCustomer().getName(),
                List.of(
                        new JournalEntryService.LinePosting(bankAccount.getGlAccount().getId(), amount, null, invoice.getInvoiceNumber()),
                        new JournalEntryService.LinePosting(accountsReceivable.getId(), null, amount, invoice.getInvoiceNumber())
                ),
                JournalEntry.SourceType.INVOICE_PAYMENT,
                invoice.getId(),
                createdBy);

        InvoicePayment payment = paymentRepo.save(InvoicePayment.builder()
                .invoice(invoice)
                .bankAccount(bankAccount)
                .amount(amount)
                .paymentDate(paymentDate)
                .paymentMethod(paymentMethod)
                .referenceNumber(req.referenceNumber())
                .journalEntry(entry)
                .createdBy(createdBy)
                .build());

        BigDecimal newPaidAmount = invoice.getPaidAmount().add(amount);
        invoice.setPaidAmount(newPaidAmount);
        invoice.setStatus(newPaidAmount.compareTo(invoice.getTotal()) >= 0
                ? Invoice.InvoiceStatus.PAID
                : Invoice.InvoiceStatus.PARTIALLY_PAID);
        invoiceRepo.save(invoice);

        return InvoicePaymentDto.from(payment);
    }

    @Transactional(readOnly = true)
    public List<InvoicePaymentDto> listForInvoice(UUID invoiceId) {
        return paymentRepo.findByInvoiceIdOrderByPaymentDateDesc(invoiceId).stream()
                .map(InvoicePaymentDto::from)
                .toList();
    }

    private PaymentMethod parseMethod(String method) {
        try {
            return PaymentMethod.valueOf(method);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Invalid payment method: " + method);
        }
    }
}
