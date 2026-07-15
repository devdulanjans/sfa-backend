package com.sfa.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesRepPerformanceDto(
        UUID       repId,
        String     repName,
        BigDecimal revenue,
        long       orderCount,
        BigDecimal avgOrderValue
) {}
