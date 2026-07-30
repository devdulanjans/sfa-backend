package com.sfa.service;

import com.sfa.dto.ReceivableCustomerDto;
import com.sfa.dto.ReceivableInvoiceDto;
import com.sfa.entity.Invoice;
import com.sfa.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountsReceivableService {

    private static final List<Invoice.InvoiceStatus> OUTSTANDING_STATUSES =
            List.of(Invoice.InvoiceStatus.ISSUED, Invoice.InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoiceRepo;

    public List<ReceivableCustomerDto> listOutstandingByCustomer() {
        return invoiceRepo.findOutstandingByCustomer(OUTSTANDING_STATUSES).stream()
                .map(row -> new ReceivableCustomerDto((UUID) row[0], (String) row[1], (BigDecimal) row[2]))
                .toList();
    }

    public List<ReceivableInvoiceDto> listOutstandingForCustomer(UUID customerId) {
        return invoiceRepo.findOutstandingForCustomer(customerId, OUTSTANDING_STATUSES).stream()
                .map(ReceivableInvoiceDto::from)
                .toList();
    }
}
