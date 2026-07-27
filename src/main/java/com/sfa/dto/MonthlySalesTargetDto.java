package com.sfa.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A monthly target plus its live-computed status. achievedQty/remainingQty/progressPct are
 * always computed fresh from approved+invoiced order quantities (see
 * OrderRepository.sumAchievedQty) — never stored — so a cancelled order silently drops out with
 * no reconciliation step needed. todayTarget/achievedToday are only populated when the target's
 * year/month is the current month (they're meaningless for a past or future month).
 */
public record MonthlySalesTargetDto(
        UUID id,
        UUID salesRepId,
        String salesRepName,
        UUID productId,
        String productName,
        String productCode,
        int targetYear,
        int targetMonth,
        BigDecimal targetQty,
        BigDecimal achievedQty,
        BigDecimal remainingQty,
        BigDecimal todayTarget,
        BigDecimal achievedToday,
        double progressPct
) {}
