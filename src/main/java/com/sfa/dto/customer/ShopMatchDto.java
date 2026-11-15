package com.sfa.dto.customer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A registered shop that may be the one being added as a customer. Profile only — never which
 * other businesses serve it, nor any of their terms, balances or orders.
 *
 * @param reasons            PHONE, TAX or NEARBY — why it matched
 * @param existingCustomerId set when the caller's own channel already has this shop as a customer
 */
public record ShopMatchDto(
        UUID shopId,
        String name,
        String phone,
        String address,
        String area,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distanceMeters,
        List<String> reasons,
        UUID existingCustomerId,
        String existingCustomerCode
) {}
