package com.sfa.service;

import com.sfa.dto.RecordVendorBillPaymentRequest;
import com.sfa.dto.VendorBillPaymentDto;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.PaymentMethod;
import com.sfa.entity.VendorBill;
import com.sfa.entity.VendorBillPayment;
import com.sfa.exception.BusinessException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.VendorBillPaymentRepository;
import com.sfa.repository.VendorBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorBillPaymentService {

    private static final String ACCOUNTS_PAYABLE_CODE = "2100";

    private final VendorBillService vendorBillService;
    private final VendorBillRepository vendorBillRepo;
    private final VendorBillPaymentRepository paymentRepo;
    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;

    public VendorBillPaymentDto recordPayment(UUID billId, RecordVendorBillPaymentRequest req, UUID createdBy) {
        VendorBill bill = vendorBillService.getOrThrow(billId);
        if (bill.getStatus() == VendorBill.BillStatus.PAID || bill.getStatus() == VendorBill.BillStatus.CANCELLED) {
            throw new BusinessException("Bill is already " + bill.getStatus());
        }
        BigDecimal outstanding = bill.getTotal().subtract(bill.getPaidAmount());
        BigDecimal amount = req.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException("Payment amount exceeds outstanding balance of " + outstanding);
        }

        BankAccount bankAccount = bankAccountRepo.findById(req.bankAccountId())
                .orElseThrow(() -> new BusinessException("Bank account not found: " + req.bankAccountId()));
        ChartOfAccount accountsPayable = accountRepo.findByAccountCode(ACCOUNTS_PAYABLE_CODE)
                .orElseThrow(() -> new BusinessException("Accounts Payable system account is missing"));
        PaymentMethod paymentMethod = parseMethod(req.paymentMethod());
        LocalDate paymentDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now();

        JournalEntry entry = journalEntryService.postEntry(
                paymentDate,
                "Payment for bill " + bill.getBillNumber() + " — " + bill.getVendor().getName(),
                List.of(
                        new JournalEntryService.LinePosting(accountsPayable.getId(), amount, null, bill.getBillNumber()),
                        new JournalEntryService.LinePosting(bankAccount.getGlAccount().getId(), null, amount, bill.getBillNumber())
                ),
                JournalEntry.SourceType.VENDOR_BILL_PAYMENT,
                bill.getId(),
                createdBy);

        VendorBillPayment payment = paymentRepo.save(VendorBillPayment.builder()
                .vendorBill(bill)
                .bankAccount(bankAccount)
                .amount(amount)
                .paymentDate(paymentDate)
                .paymentMethod(paymentMethod)
                .referenceNumber(req.referenceNumber())
                .journalEntry(entry)
                .createdBy(createdBy)
                .build());

        BigDecimal newPaidAmount = bill.getPaidAmount().add(amount);
        bill.setPaidAmount(newPaidAmount);
        bill.setStatus(newPaidAmount.compareTo(bill.getTotal()) >= 0
                ? VendorBill.BillStatus.PAID
                : VendorBill.BillStatus.PARTIALLY_PAID);
        vendorBillRepo.save(bill);

        return VendorBillPaymentDto.from(payment);
    }

    @Transactional(readOnly = true)
    public List<VendorBillPaymentDto> listForBill(UUID billId) {
        return paymentRepo.findByVendorBillIdOrderByPaymentDateDesc(billId).stream()
                .map(VendorBillPaymentDto::from)
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
