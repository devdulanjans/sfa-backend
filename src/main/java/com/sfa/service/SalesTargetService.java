package com.sfa.service;

import com.sfa.dto.MonthlySalesTargetDto;
import com.sfa.entity.MonthlySalesTarget;
import com.sfa.entity.Product;
import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.MonthlySalesTargetRepository;
import com.sfa.repository.OrderRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesTargetService {

    private final MonthlySalesTargetRepository targetRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final WorkingCalendarService workingCalendarService;
    private final SystemSettingService systemSettingService;

    public MonthlySalesTargetDto create(UUID repId, UUID productId, int year, int month, BigDecimal qty, UUID createdBy) {
        requireEnabled();
        User rep = userRepo.findById(repId)
                .orElseThrow(() -> new ResourceNotFoundException("User", repId));
        if (!Role.SALES_REP.equals(rep.getRole().getName())) {
            throw new BusinessException("Targets can only be assigned to SALES_REP users");
        }
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Target quantity must be greater than zero");
        }
        if (targetRepo.existsBySalesRepIdAndProductIdAndTargetYearAndTargetMonth(repId, productId, year, month)) {
            throw new BusinessException("A target already exists for this rep, product, and month");
        }

        MonthlySalesTarget t = MonthlySalesTarget.builder()
                .salesRep(rep)
                .product(product)
                .targetYear(year)
                .targetMonth(month)
                .targetQty(qty)
                .createdBy(createdBy)
                .build();
        return computeStatus(targetRepo.save(t));
    }

    public MonthlySalesTargetDto update(UUID id, BigDecimal qty) {
        requireEnabled();
        MonthlySalesTarget t = findOrThrow(id);
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Target quantity must be greater than zero");
        }
        t.setTargetQty(qty);
        return computeStatus(targetRepo.save(t));
    }

    public void delete(UUID id) {
        requireEnabled();
        targetRepo.delete(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<MonthlySalesTargetDto> list(UUID repId, Integer year, Integer month, Pageable pageable) {
        return targetRepo.findFiltered(repId, year, month, pageable).map(this::computeStatus);
    }

    /** This rep's targets for the current month, with today's computed daily target —
     *  used by the mobile dashboard card. Empty list whenever the feature is disabled, so the
     *  caller doesn't need any separate "is this even on" check. */
    @Transactional(readOnly = true)
    public List<MonthlySalesTargetDto> getMyToday(UUID repId) {
        if (!systemSettingService.isSalesTargetEnabled()) return List.of();
        LocalDate today = LocalDate.now();
        return targetRepo.findBySalesRepIdAndTargetYearAndTargetMonth(repId, today.getYear(), today.getMonthValue())
                .stream()
                .map(this::computeStatus)
                .toList();
    }

    private MonthlySalesTargetDto computeStatus(MonthlySalesTarget t) {
        LocalDate monthStart = LocalDate.of(t.getTargetYear(), t.getTargetMonth(), 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        Instant monthFromInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthToExclusive = monthEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal achieved = orderRepo.sumAchievedQty(t.getSalesRep().getId(), t.getProduct().getId(),
                OrderRepository.ACHIEVED_STATUSES, monthFromInstant, monthToExclusive);
        BigDecimal remaining = t.getTargetQty().subtract(achieved).max(BigDecimal.ZERO);

        LocalDate today = LocalDate.now();
        boolean isCurrentMonth = today.getYear() == t.getTargetYear() && today.getMonthValue() == t.getTargetMonth();

        BigDecimal todayTarget = null;
        BigDecimal achievedToday = null;
        if (isCurrentMonth) {
            int workingDaysLeft = workingCalendarService.countWorkingDays(today, monthEnd);
            todayTarget = workingDaysLeft > 0
                    ? remaining.divide(BigDecimal.valueOf(workingDaysLeft), 2, RoundingMode.HALF_UP)
                    : remaining;

            Instant todayFromInstant = today.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant todayToExclusive = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            achievedToday = orderRepo.sumAchievedQty(t.getSalesRep().getId(), t.getProduct().getId(),
                    OrderRepository.ACHIEVED_STATUSES, todayFromInstant, todayToExclusive);
        }

        double progressPct = t.getTargetQty().compareTo(BigDecimal.ZERO) > 0
                ? achieved.divide(t.getTargetQty(), 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0;

        return new MonthlySalesTargetDto(
                t.getId(),
                t.getSalesRep().getId(),
                t.getSalesRep().getFullName(),
                t.getProduct().getId(),
                t.getProduct().getName(),
                t.getProduct().getProductCode(),
                t.getTargetYear(),
                t.getTargetMonth(),
                t.getTargetQty(),
                achieved,
                remaining,
                todayTarget,
                achievedToday,
                progressPct
        );
    }

    private void requireEnabled() {
        if (!systemSettingService.isSalesTargetEnabled()) {
            throw new BusinessException("Sales Target feature is disabled");
        }
    }

    private MonthlySalesTarget findOrThrow(UUID id) {
        return targetRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MonthlySalesTarget", id));
    }
}
