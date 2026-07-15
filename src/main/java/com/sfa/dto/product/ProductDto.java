package com.sfa.dto.product;

import com.sfa.entity.Product;
import com.sfa.entity.StockLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String productCode,
        String barcode,
        String name,
        String description,
        UUID categoryId,
        String categoryName,
        UUID unitId,
        String unitName,
        String unitAbbreviation,
        Double defaultPrice,
        Double purchasePrice,
        Double marginPct,
        Double profitPerUnit,
        Double taxRate,
        Double maxDiscountAmount,
        String status,
        Double stockOnHand,
        Double stockAvailable
) {
    public static ProductDto from(Product p) {
        return from(p, null);
    }

    public static ProductDto from(Product p, StockLevel stock) {
        BigDecimal sell = p.getDefaultPrice();
        BigDecimal cost = p.getPurchasePrice();
        Double profitPerUnit = null;
        Double marginPct = null;
        if (sell != null && cost != null) {
            BigDecimal profit = sell.subtract(cost);
            profitPerUnit = profit.doubleValue();
            if (sell.compareTo(BigDecimal.ZERO) > 0) {
                marginPct = profit.divide(sell, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
            }
        }

        Double stockOnHand = stock != null ? stock.getOnHand().doubleValue() : null;
        Double stockAvailable = stock != null
                ? stock.getOnHand().subtract(stock.getReserved()).doubleValue() : null;

        return new ProductDto(
                p.getId(),
                p.getProductCode(),
                p.getBarcode(),
                p.getName(),
                p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getUnit() != null ? p.getUnit().getId() : null,
                p.getUnit() != null ? p.getUnit().getName() : null,
                p.getUnit() != null ? p.getUnit().getAbbreviation() : null,
                sell != null ? sell.doubleValue() : null,
                cost != null ? cost.doubleValue() : null,
                marginPct,
                profitPerUnit,
                p.getTaxRate() != null ? p.getTaxRate().doubleValue() : null,
                p.getMaxDiscountAmount() != null ? p.getMaxDiscountAmount().doubleValue() : null,
                p.getStatus() != null ? p.getStatus().name() : null,
                stockOnHand,
                stockAvailable
        );
    }
}
