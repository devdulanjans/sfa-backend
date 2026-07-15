package com.sfa.service;

import com.sfa.entity.Order;
import com.sfa.entity.StockBatch;
import com.sfa.entity.StockBatchConsumption;
import com.sfa.entity.StockLevel;
import com.sfa.entity.StockMovement;
import com.sfa.exception.BusinessException;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.StockBatchConsumptionRepository;
import com.sfa.repository.StockBatchRepository;
import com.sfa.repository.StockLevelRepository;
import com.sfa.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final StockLevelRepository            stockLevelRepo;
    private final StockMovementRepository         movementRepo;
    private final StockBatchRepository            stockBatchRepo;
    private final StockBatchConsumptionRepository batchConsumptionRepo;
    private final ProductRepository               productRepo;
    private final SystemSettingService            systemSettingService;

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return systemSettingService.isInventoryEnabled();
    }

    @Transactional(readOnly = true)
    public StockLevel getStock(UUID productId) {
        return stockLevelRepo.findByProductId(productId)
                .orElseGet(() -> StockLevel.builder()
                        .productId(productId)
                        .onHand(BigDecimal.ZERO)
                        .reserved(BigDecimal.ZERO)
                        .build());
    }

    public StockLevel adjust(UUID productId, BigDecimal quantity, String notes, UUID userId) {
        stockLevelRepo.ensureExists(productId);

        StockLevel level = stockLevelRepo.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException("Stock level not found for product " + productId));

        BigDecimal newBalance = level.getOnHand().add(quantity);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Adjustment would result in negative stock (current: "
                    + level.getOnHand() + ", change: " + quantity + ")");
        }

        level.setOnHand(newBalance);
        StockLevel saved = stockLevelRepo.save(level);

        StockMovement movement = movementRepo.save(StockMovement.builder()
                .productId(productId)
                .type(StockMovement.MovementType.ADJUSTMENT)
                .quantity(quantity)
                .balanceAfter(newBalance)
                .notes(notes)
                .createdBy(userId)
                .build());

        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            movement.setTotalCost(consumeFifo(productId, quantity.abs(), movement.getId()));
            movementRepo.save(movement);
        }

        return saved;
    }

    /** Receives a new stock batch (its own cost/quantity/received date) and bumps the aggregate on-hand total. */
    public StockLevel receiveStock(UUID productId, BigDecimal receivedQty, BigDecimal unitCost,
                                    LocalDate receivedDate, String notes, UUID userId) {
        if (!isEnabled()) {
            throw new BusinessException("Enable inventory tracking before receiving stock");
        }
        if (receivedQty == null || receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Received quantity must be greater than zero");
        }
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Unit cost must be zero or greater");
        }
        if (receivedDate == null) {
            throw new BusinessException("Received date is required");
        }

        stockBatchRepo.save(StockBatch.builder()
                .productId(productId)
                .receivedQty(receivedQty)
                .remainingQty(receivedQty)
                .unitCost(unitCost)
                .receivedDate(receivedDate)
                .notes(notes)
                .createdBy(userId)
                .build());

        stockLevelRepo.ensureExists(productId);
        StockLevel level = stockLevelRepo.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException("Stock level not found for product " + productId));

        BigDecimal newBalance = level.getOnHand().add(receivedQty);
        level.setOnHand(newBalance);
        StockLevel saved = stockLevelRepo.save(level);

        movementRepo.save(StockMovement.builder()
                .productId(productId)
                .type(StockMovement.MovementType.STOCK_RECEIVE)
                .quantity(receivedQty)
                .balanceAfter(newBalance)
                .totalCost(receivedQty.multiply(unitCost))
                .notes(notes)
                .createdBy(userId)
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<StockBatch> listBatches(UUID productId) {
        return stockBatchRepo.findAllForProduct(productId);
    }

    /**
     * Consumes {@code qty} from the product's oldest available batches (FIFO), recording a
     * {@link StockBatchConsumption} row per batch touched so the exact consumption can be reversed later.
     * If batch data runs short (e.g. legacy stock received before batch tracking existed), the uncovered
     * remainder is costed using the product's {@code purchasePrice} as a best-effort fallback and is not
     * attributed to any batch — it never blocks the deduction itself.
     */
    private BigDecimal consumeFifo(UUID productId, BigDecimal qty, UUID movementId) {
        BigDecimal remaining = qty;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (StockBatch batch : stockBatchRepo.findAvailableForUpdate(productId)) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal take = batch.getRemainingQty().min(remaining);
            if (take.compareTo(BigDecimal.ZERO) <= 0) continue;

            batch.setRemainingQty(batch.getRemainingQty().subtract(take));
            stockBatchRepo.save(batch);

            batchConsumptionRepo.save(StockBatchConsumption.builder()
                    .batch(batch)
                    .movementId(movementId)
                    .quantity(take)
                    .unitCost(batch.getUnitCost())
                    .build());

            totalCost = totalCost.add(take.multiply(batch.getUnitCost()));
            remaining = remaining.subtract(take);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fallbackCost = productRepo.findById(productId)
                    .map(p -> p.getPurchasePrice())
                    .orElse(null);
            if (fallbackCost != null) {
                totalCost = totalCost.add(remaining.multiply(fallbackCost));
            }
        }

        return totalCost;
    }

    /** Reverses a previous FIFO consumption, restoring quantity back to the exact batches it was drawn from. */
    private BigDecimal restoreFifo(UUID originalMovementId) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatchConsumption consumption : batchConsumptionRepo.findByMovementId(originalMovementId)) {
            StockBatch batch = consumption.getBatch();
            batch.setRemainingQty(batch.getRemainingQty().add(consumption.getQuantity()));
            stockBatchRepo.save(batch);
            totalCost = totalCost.add(consumption.getQuantity().multiply(consumption.getUnitCost()));
        }
        return totalCost;
    }

    // Called inside OrderService.approveOrder — must be invoked within the same transaction
    public void deductForOrder(Order order, UUID userId) {
        if (!isEnabled()) return;

        for (var item : order.getItems()) {
            UUID productId = item.getProduct().getId();
            BigDecimal qty = item.getQuantity();

            stockLevelRepo.ensureExists(productId);

            StockLevel level = stockLevelRepo.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new BusinessException("Stock level not initialised for product " + productId));

            if (level.getOnHand().compareTo(qty) < 0) {
                throw new BusinessException("Insufficient stock for product '"
                        + item.getProduct().getName()
                        + "' (available: " + level.getOnHand() + ", requested: " + qty + ")");
            }

            BigDecimal newBalance = level.getOnHand().subtract(qty);
            level.setOnHand(newBalance);
            stockLevelRepo.save(level);

            StockMovement movement = movementRepo.save(StockMovement.builder()
                    .productId(productId)
                    .type(StockMovement.MovementType.ORDER_OUT)
                    .quantity(qty.negate())
                    .balanceAfter(newBalance)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .createdBy(userId)
                    .build());

            movement.setTotalCost(consumeFifo(productId, qty, movement.getId()));
            movementRepo.save(movement);
        }
    }

    // Called inside OrderService.cancelOrder when the cancelled order was APPROVED
    public void restoreForOrder(Order order, UUID userId) {
        if (!isEnabled()) return;

        for (var item : order.getItems()) {
            UUID productId = item.getProduct().getId();
            BigDecimal qty = item.getQuantity();

            stockLevelRepo.ensureExists(productId);

            StockLevel level = stockLevelRepo.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new BusinessException("Stock level not found for product " + productId));

            BigDecimal newBalance = level.getOnHand().add(qty);
            level.setOnHand(newBalance);
            stockLevelRepo.save(level);

            StockMovement movement = movementRepo.save(StockMovement.builder()
                    .productId(productId)
                    .type(StockMovement.MovementType.ORDER_CANCEL_IN)
                    .quantity(qty)
                    .balanceAfter(newBalance)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .createdBy(userId)
                    .build());

            movementRepo.findByReferenceTypeAndReferenceIdAndProductIdAndType(
                    "ORDER", order.getId(), productId, StockMovement.MovementType.ORDER_OUT
            ).ifPresent(original -> {
                movement.setTotalCost(restoreFifo(original.getId()));
                movementRepo.save(movement);
            });
        }
    }

    // Called inside PosService for deduction and void-restore
    public void deductForPosSale(UUID saleId, UUID productId, BigDecimal qty, UUID userId) {
        doPosSaleMovement(saleId, productId, qty.negate(), StockMovement.MovementType.POS_OUT, userId);
    }

    public void restoreForPosSale(UUID saleId, UUID productId, BigDecimal qty, UUID userId) {
        doPosSaleMovement(saleId, productId, qty, StockMovement.MovementType.POS_VOID_IN, userId);
    }

    private void doPosSaleMovement(UUID saleId, UUID productId, BigDecimal delta,
                                   StockMovement.MovementType type, UUID userId) {
        if (!isEnabled()) return;

        stockLevelRepo.ensureExists(productId);

        StockLevel level = stockLevelRepo.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException("Stock level not found for product " + productId));

        if (delta.compareTo(BigDecimal.ZERO) < 0
                && level.getOnHand().compareTo(delta.abs()) < 0) {
            throw new BusinessException("Insufficient stock (available: " + level.getOnHand() + ")");
        }

        BigDecimal newBalance = level.getOnHand().add(delta);
        level.setOnHand(newBalance);
        stockLevelRepo.save(level);

        StockMovement movement = movementRepo.save(StockMovement.builder()
                .productId(productId)
                .type(type)
                .quantity(delta)
                .balanceAfter(newBalance)
                .referenceType("POS_SALE")
                .referenceId(saleId)
                .createdBy(userId)
                .build());

        if (type == StockMovement.MovementType.POS_OUT) {
            movement.setTotalCost(consumeFifo(productId, delta.abs(), movement.getId()));
            movementRepo.save(movement);
        } else {
            movementRepo.findByReferenceTypeAndReferenceIdAndProductIdAndType(
                    "POS_SALE", saleId, productId, StockMovement.MovementType.POS_OUT
            ).ifPresent(original -> {
                movement.setTotalCost(restoreFifo(original.getId()));
                movementRepo.save(movement);
            });
        }
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getMovements(UUID productId, String typeStr, int page, int size) {
        StockMovement.MovementType type = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try { type = StockMovement.MovementType.valueOf(typeStr); } catch (IllegalArgumentException ignored) {}
        }
        var pageable = PageRequest.of(page, size);
        if (productId != null && type != null) return movementRepo.findByProductIdAndType(productId, type, pageable);
        if (productId != null)                 return movementRepo.findByProductId(productId, pageable);
        if (type != null)                      return movementRepo.findByType(type, pageable);
        return movementRepo.findAllOrdered(pageable);
    }
}
