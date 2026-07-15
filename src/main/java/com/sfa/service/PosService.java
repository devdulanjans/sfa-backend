package com.sfa.service;

import com.sfa.dto.CashierSummaryDto;
import com.sfa.dto.PosReportRowDto;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.DrawerSessionRepository;
import com.sfa.repository.PosSalePaymentRepository;
import com.sfa.repository.PosSaleRepository;
import com.sfa.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PosService {

    private final PosSaleRepository        posSaleRepo;
    private final PosSalePaymentRepository posSalePaymentRepo;
    private final CustomerRepository       customerRepo;
    private final ProductRepository        productRepo;
    private final InventoryService         inventoryService;
    private final SystemSettingService     systemSettingService;
    private final DrawerSessionRepository  drawerSessionRepo;
    private final PricingEngine            pricingEngine;

    @PersistenceContext
    private EntityManager em;

    public record PosItemRequest(
            UUID productId, String productName,
            BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal discountPct, BigDecimal taxPct) {}

    public record CreatePosSaleRequest(
            UUID customerId, String customerName,
            String paymentMethod,
            BigDecimal amountTendered,
            BigDecimal amountPaid,
            String cardLast4,
            String notes,
            List<PosItemRequest> items) {}

    public PosSale createSale(CreatePosSaleRequest req, UUID userId) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new BusinessException("Sale must have at least one item");
        }
        if (!drawerSessionRepo.existsByCashierIdAndStatus(userId, DrawerSession.Status.OPEN)) {
            throw new BusinessException("Open your cash drawer before processing sales");
        }

        PosSale.PaymentMethod pm;
        try {
            pm = PosSale.PaymentMethod.valueOf(req.paymentMethod());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + req.paymentMethod());
        }

        if (pm == PosSale.PaymentMethod.CREDIT && req.customerId() == null) {
            throw new BusinessException("Credit sales require a customer to be selected");
        }

        // Resolve optional customer
        Customer customer = null;
        if (req.customerId() != null) {
            customer = customerRepo.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
        }

        // Build and persist the sale header first (items via cascade)
        String saleNumber = generateSaleNumber();

        PosSale sale = PosSale.builder()
                .saleNumber(saleNumber)
                .customer(customer)
                .customerName(customer != null ? customer.getName() : req.customerName())
                .paymentMethod(pm)
                .subtotal(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .amountTendered(pm == PosSale.PaymentMethod.CASH ? req.amountTendered() : null)
                .notes(req.notes())
                .createdBy(userId)
                .build();

        BigDecimal subtotal       = BigDecimal.ZERO;
        BigDecimal discountTotal  = BigDecimal.ZERO;
        BigDecimal taxTotal       = BigDecimal.ZERO;
        boolean    taxEnabled     = systemSettingService.isPosTaxEnabled();

        for (PosItemRequest ir : req.items()) {
            BigDecimal discPct = ir.discountPct() != null ? ir.discountPct() : BigDecimal.ZERO;

            Product product = ir.productId() != null ? productRepo.findById(ir.productId()).orElse(null) : null;

            // For customer-linked sales, tax is authoritatively resolved server-side
            // from the customer's VAT-registration status — the client-supplied
            // taxPct is only trusted for anonymous walk-in sales (no customer) or
            // ad-hoc items with no product record to look up a tax rate from.
            BigDecimal taxPct;
            if (taxEnabled && product != null && req.customerId() != null) {
                taxPct = pricingEngine.resolveTaxPct(product, req.customerId());
            } else {
                taxPct = taxEnabled && ir.taxPct() != null ? ir.taxPct() : BigDecimal.ZERO;
            }

            if (product != null && product.getMaxDiscountAmount() != null) {
                BigDecimal perUnitDiscount = ir.unitPrice().multiply(discPct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (perUnitDiscount.compareTo(product.getMaxDiscountAmount()) > 0) {
                    throw new BusinessException("Discount for '" + product.getName() + "' exceeds the maximum allowed (LKR "
                            + product.getMaxDiscountAmount() + " per unit)");
                }
            }

            BigDecimal gross          = ir.unitPrice().multiply(ir.quantity());
            BigDecimal discountAmount = gross.multiply(discPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal afterDiscount  = gross.subtract(discountAmount);
            BigDecimal taxAmount      = afterDiscount.multiply(taxPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal      = afterDiscount.add(taxAmount);

            String resolvedName = ir.productName() != null && !ir.productName().isBlank()
                    ? ir.productName()
                    : (product != null ? product.getName() : "Item");

            PosSaleItem item = PosSaleItem.builder()
                    .sale(sale)
                    .productId(ir.productId())
                    .productName(resolvedName)
                    .quantity(ir.quantity())
                    .unitPrice(ir.unitPrice())
                    .discountPct(discPct)
                    .discountAmount(discountAmount)
                    .taxPct(taxPct)
                    .taxAmount(taxAmount)
                    .lineTotal(lineTotal)
                    .build();

            sale.getItems().add(item);
            subtotal      = subtotal.add(gross);
            discountTotal = discountTotal.add(discountAmount);
            taxTotal      = taxTotal.add(taxAmount);
        }

        BigDecimal total = subtotal.subtract(discountTotal).add(taxTotal);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discountTotal);
        sale.setTaxAmount(taxTotal);
        sale.setTotal(total);

        BigDecimal paidNow = BigDecimal.ZERO;

        if (pm == PosSale.PaymentMethod.CASH) {
            BigDecimal tendered = req.amountTendered() != null ? req.amountTendered() : BigDecimal.ZERO;
            if (tendered.compareTo(total) < 0) {
                throw new BusinessException("Amount tendered (LKR " + tendered + ") is less than total (LKR " + total + ")");
            }
            sale.setChangeAmount(tendered.subtract(total));
            sale.setAmountPaid(total);
            sale.setBalanceDue(BigDecimal.ZERO);
            sale.setCreditStatus(PosSale.CreditStatus.NOT_APPLICABLE);
        } else if (pm == PosSale.PaymentMethod.CARD) {
            String cardLast4 = req.cardLast4() != null ? req.cardLast4().trim() : "";
            if (!cardLast4.matches("\\d{4}")) {
                throw new BusinessException("Card payments require the last 4 digits of the card number");
            }
            sale.setCardLast4(cardLast4);
            sale.setAmountPaid(total);
            sale.setBalanceDue(BigDecimal.ZERO);
            sale.setCreditStatus(PosSale.CreditStatus.NOT_APPLICABLE);
        } else { // CREDIT
            paidNow = req.amountPaid() != null ? req.amountPaid() : BigDecimal.ZERO;
            if (paidNow.compareTo(BigDecimal.ZERO) < 0 || paidNow.compareTo(total) > 0) {
                throw new BusinessException("Amount paid must be between 0 and the total (LKR " + total + ")");
            }
            BigDecimal balanceDue = total.subtract(paidNow);
            sale.setAmountPaid(paidNow);
            sale.setBalanceDue(balanceDue);
            sale.setCreditStatus(balanceDue.compareTo(BigDecimal.ZERO) == 0
                    ? PosSale.CreditStatus.PAID
                    : (paidNow.compareTo(BigDecimal.ZERO) == 0 ? PosSale.CreditStatus.UNPAID : PosSale.CreditStatus.PARTIALLY_PAID));

            if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
                UUID customerId = customer.getId();
                Customer lockedCustomer = customerRepo.findByIdForUpdate(customerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
                lockedCustomer.setCurrentBalance(lockedCustomer.getCurrentBalance().add(balanceDue));
                customerRepo.save(lockedCustomer);
            }
        }

        PosSale saved = posSaleRepo.save(sale);

        if (pm == PosSale.PaymentMethod.CREDIT && paidNow.compareTo(BigDecimal.ZERO) > 0) {
            posSalePaymentRepo.save(PosSalePayment.builder()
                    .sale(saved)
                    .customer(customer)
                    .amount(paidNow)
                    .paymentMethod(PosSalePayment.SettlementMethod.CASH)
                    .paymentType(PosSalePayment.PaymentType.INITIAL)
                    .balanceAfter(saved.getBalanceDue())
                    .recordedBy(userId)
                    .build());
        }

        // Deduct inventory if enabled (after successful save so we have the sale ID)
        if (inventoryService.isEnabled()) {
            for (PosSaleItem item : saved.getItems()) {
                if (item.getProductId() != null) {
                    inventoryService.deductForPosSale(saved.getId(), item.getProductId(), item.getQuantity(), userId);
                }
            }
        }

        return saved;
    }

    public PosSale voidSale(UUID saleId, UUID userId) {
        PosSale sale = posSaleRepo.findByIdWithItems(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("PosSale", saleId));

        if (sale.getStatus() == PosSale.SaleStatus.VOIDED) {
            throw new BusinessException("Sale is already voided");
        }

        if (sale.getPaymentMethod() == PosSale.PaymentMethod.CREDIT
                && sale.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot void a credit sale that already has payments recorded against it");
        }

        if (sale.getPaymentMethod() == PosSale.PaymentMethod.CREDIT
                && sale.getBalanceDue().compareTo(BigDecimal.ZERO) > 0
                && sale.getCustomer() != null) {
            Customer lockedCustomer = customerRepo.findByIdForUpdate(sale.getCustomer().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", sale.getCustomer().getId()));
            lockedCustomer.setCurrentBalance(lockedCustomer.getCurrentBalance().subtract(sale.getBalanceDue()));
            customerRepo.save(lockedCustomer);
        }

        sale.setStatus(PosSale.SaleStatus.VOIDED);
        PosSale saved = posSaleRepo.save(sale);

        // Restore inventory if enabled
        if (inventoryService.isEnabled()) {
            for (PosSaleItem item : saved.getItems()) {
                if (item.getProductId() != null) {
                    inventoryService.restoreForPosSale(saved.getId(), item.getProductId(), item.getQuantity(), userId);
                }
            }
        }

        return saved;
    }

    /** Records a settlement payment against a single credit sale. */
    public PosSale recordPaymentForSale(UUID saleId, BigDecimal amount, String methodStr, String notes, UUID userId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        PosSale sale = posSaleRepo.findByIdForUpdate(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("PosSale", saleId));

        if (sale.getStatus() != PosSale.SaleStatus.COMPLETED) {
            throw new BusinessException("Cannot record a payment against a voided sale");
        }
        if (sale.getCreditStatus() == PosSale.CreditStatus.NOT_APPLICABLE
                || sale.getCreditStatus() == PosSale.CreditStatus.PAID) {
            throw new BusinessException("This sale has no outstanding balance");
        }
        if (amount.compareTo(sale.getBalanceDue()) > 0) {
            throw new BusinessException("Payment (LKR " + amount + ") exceeds outstanding balance (LKR " + sale.getBalanceDue() + ")");
        }
        if (sale.getCustomer() == null) {
            throw new BusinessException("This credit sale has no customer on record and cannot be settled");
        }

        Customer customer = customerRepo.findByIdForUpdate(sale.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", sale.getCustomer().getId()));

        applyPayment(sale, customer, amount, methodStr, notes, userId, PosSalePayment.PaymentType.SETTLEMENT);

        return posSaleRepo.save(sale);
    }

    /** Records a single payment allocated FIFO (oldest first) across a customer's open credit sales. */
    public List<PosSale> recordBulkPaymentForCustomer(UUID customerId, BigDecimal amount, String methodStr, String notes, UUID userId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        Customer customer = customerRepo.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        List<PosSale> openSales = posSaleRepo.findOpenCreditSalesForCustomer(customerId);
        BigDecimal totalOutstanding = openSales.stream().map(PosSale::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(totalOutstanding) > 0) {
            throw new BusinessException("Payment (LKR " + amount + ") exceeds total outstanding balance (LKR " + totalOutstanding + ")");
        }

        BigDecimal remaining = amount;
        List<PosSale> affected = new java.util.ArrayList<>();
        for (PosSale sale : openSales) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal portion = remaining.min(sale.getBalanceDue());
            applyPayment(sale, customer, portion, methodStr, notes, userId, PosSalePayment.PaymentType.SETTLEMENT);
            posSaleRepo.save(sale);
            affected.add(sale);
            remaining = remaining.subtract(portion);
        }

        return affected;
    }

    private void applyPayment(PosSale sale, Customer customer, BigDecimal amount, String methodStr, String notes,
                               UUID userId, PosSalePayment.PaymentType paymentType) {
        PosSalePayment.SettlementMethod method;
        try {
            method = methodStr != null ? PosSalePayment.SettlementMethod.valueOf(methodStr) : PosSalePayment.SettlementMethod.CASH;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + methodStr);
        }

        sale.setAmountPaid(sale.getAmountPaid().add(amount));
        sale.setBalanceDue(sale.getBalanceDue().subtract(amount));
        sale.setCreditStatus(sale.getBalanceDue().compareTo(BigDecimal.ZERO) == 0
                ? PosSale.CreditStatus.PAID
                : PosSale.CreditStatus.PARTIALLY_PAID);

        customer.setCurrentBalance(customer.getCurrentBalance().subtract(amount));
        customerRepo.save(customer);

        posSalePaymentRepo.save(PosSalePayment.builder()
                .sale(sale)
                .customer(customer)
                .amount(amount)
                .paymentMethod(method)
                .paymentType(paymentType)
                .balanceAfter(sale.getBalanceDue())
                .notes(notes)
                .recordedBy(userId)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<PosSale> listCreditBills(UUID customerId, String creditStatusStr, Instant dateFrom, Instant dateTo, Pageable pageable) {
        PosSale.CreditStatus creditStatus = parseCreditStatus(creditStatusStr);
        return posSaleRepo.findCreditBills(customerId, creditStatus, dateFrom, dateTo, pageable);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumOutstandingCreditBalance(UUID customerId, String creditStatusStr, Instant dateFrom, Instant dateTo) {
        PosSale.CreditStatus creditStatus = parseCreditStatus(creditStatusStr);
        return posSaleRepo.sumBalanceDueForCreditBills(customerId, creditStatus, dateFrom, dateTo);
    }

    private PosSale.CreditStatus parseCreditStatus(String creditStatusStr) {
        if (creditStatusStr == null || creditStatusStr.isBlank()) return null;
        try {
            return PosSale.CreditStatus.valueOf(creditStatusStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<PosSalePayment> getPaymentsForSale(UUID saleId) {
        return posSalePaymentRepo.findBySaleIdOrderByCreatedAtAsc(saleId);
    }

    @Transactional(readOnly = true)
    public Page<PosSalePayment> getPaymentsForCustomer(UUID customerId, Pageable pageable) {
        return posSalePaymentRepo.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PosSale> listSales(String statusStr, int page, int size) {
        PosSale.SaleStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try { status = PosSale.SaleStatus.valueOf(statusStr); } catch (IllegalArgumentException ignored) {}
        }
        return posSaleRepo.findFiltered(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public PosSale getSale(UUID id) {
        return posSaleRepo.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("PosSale", id));
    }

    @Transactional(readOnly = true)
    public Page<PosReportRowDto> getReport(UUID cashierId, UUID customerId, UUID productId,
                                            Instant dateFrom, Instant dateTo, Pageable pageable) {
        return posSaleRepo.findReportRaw(cashierId, customerId, productId, dateFrom, dateTo, pageable)
                .map(this::toReportRow);
    }

    @Transactional(readOnly = true)
    public List<PosReportRowDto> getReportRowsForExport(UUID cashierId, UUID customerId, UUID productId,
                                                         Instant dateFrom, Instant dateTo) {
        return posSaleRepo.findReportRaw(cashierId, customerId, productId, dateFrom, dateTo, Pageable.unpaged())
                .map(this::toReportRow)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<CashierSummaryDto> listCashiers() {
        return posSaleRepo.findCashiersRaw().stream()
                .map(r -> new CashierSummaryDto((UUID) r[0], String.valueOf(r[1])))
                .toList();
    }

    private PosReportRowDto toReportRow(Object[] r) {
        return new PosReportRowDto(
                (UUID) r[0],
                String.valueOf(r[1]),
                toInstant(r[2]),
                r[3] != null ? String.valueOf(r[3]) : "—",
                String.valueOf(r[4]),
                String.valueOf(r[5]),
                toBigDecimal(r[6]),
                toBigDecimal(r[7]),
                toBigDecimal(r[8]),
                toBigDecimal(r[9]),
                String.valueOf(r[10]),
                String.valueOf(r[11]));
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private static Instant toInstant(Object v) {
        if (v instanceof Instant i) return i;
        if (v instanceof java.sql.Timestamp t) return t.toInstant();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        throw new IllegalStateException("Unexpected timestamp type: " + (v == null ? "null" : v.getClass()));
    }

    private String generateSaleNumber() {
        Number seq = (Number) em.createNativeQuery("SELECT NEXTVAL('pos_sale_number_seq')").getSingleResult();
        String year = DateTimeFormatter.ofPattern("yyyy")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return "POS-" + year + "-" + String.format("%06d", seq.longValue());
    }
}
