package com.sfa.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerSalesDto(
        UUID customerId,
        String customerName,
        BigDecimal revenue,
        long orderCount
) {}
