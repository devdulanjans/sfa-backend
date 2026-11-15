package com.sfa.dto.publicsite;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The public website's product catalogue. Deliberately its own DTO, not ProductDto: an
 * anonymous visitor must only ever see what a shop window would show — never purchase price,
 * margin, stock or customer-specific prices.
 */
public final class PublicCatalogDto {

    private PublicCatalogDto() {}

    public record Page(
            List<Item> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            /** Every business with at least one listed product — for the filter chips. */
            List<Business> businesses
    ) {}

    public record Item(
            UUID id,
            String name,
            String productCode,
            String description,
            String categoryName,
            String unit,
            /** Price for a single unit to a customer with no special pricing, after any
             *  promotion open to everyone. */
            BigDecimal price,
            /** Price before that promotion — equal to {@code price} when none applies. */
            BigDecimal basePrice,
            Business business,
            List<Promotion> promotions,
            /** Path of the product's picture on this API (V123), or null when it has none. */
            String imageUrl
    ) {}

    /** {@code slug} is the channel's code — stable, unique, and safe to show in a URL. */
    public record Business(String slug, String name) {}

    public record Promotion(UUID id, String name, String type, String label) {}
}
