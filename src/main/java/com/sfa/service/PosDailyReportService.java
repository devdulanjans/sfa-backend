package com.sfa.service;

import com.sfa.dto.DailyReportDto;
import com.sfa.dto.DailyReportDto.PaymentMethodStat;
import com.sfa.dto.DailyTransactionDto;
import com.sfa.dto.DrawerSessionDto;
import com.sfa.entity.CashMovement;
import com.sfa.repository.CashMovementRepository;
import com.sfa.repository.PosSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosDailyReportService {

    private final PosSaleRepository      posSaleRepo;
    private final CashMovementRepository cashMovementRepo;
    private final DrawerService          drawerService;

    public DailyReportDto getDailyReport(LocalDate date, UUID cashierId) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<DrawerSessionDto> sessions = drawerService
                .listSessions(cashierId, null, dayStart, dayEnd, Pageable.unpaged())
                .getContent();

        BigDecimal totalOpeningFloat = sessions.stream()
                .map(DrawerSessionDto::openingFloat).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpectedCash = sessions.stream()
                .map(DrawerSessionDto::expectedCash).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allClosed = !sessions.isEmpty() && sessions.stream().allMatch(s -> "CLOSED".equals(s.status()));
        BigDecimal totalCountedCash = sessions.stream()
                .filter(s -> "CLOSED".equals(s.status()))
                .map(DrawerSessionDto::countedCash).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVariance = sessions.stream()
                .filter(s -> "CLOSED".equals(s.status()))
                .map(DrawerSessionDto::variance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal depositsTotal    = BigDecimal.ZERO;
        BigDecimal withdrawalsTotal = BigDecimal.ZERO;
        for (DrawerSessionDto s : sessions) {
            depositsTotal    = depositsTotal.add(cashMovementRepo.sumBySessionAndType(s.id(), CashMovement.Type.DEPOSIT));
            withdrawalsTotal = withdrawalsTotal.add(cashMovementRepo.sumBySessionAndType(s.id(), CashMovement.Type.WITHDRAWAL));
        }

        List<DailyTransactionDto> transactions = posSaleRepo.findDailyTransactionsRaw(cashierId, dayStart, dayEnd)
                .stream().map(this::toTransaction).toList();

        BigDecimal grossSalesTotal = transactions.stream()
                .map(DailyTransactionDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> revenueByMethod = new LinkedHashMap<>();
        Map<String, Long> countByMethod = new LinkedHashMap<>();
        for (DailyTransactionDto t : transactions) {
            revenueByMethod.merge(t.paymentMethod(), t.total(), BigDecimal::add);
            countByMethod.merge(t.paymentMethod(), 1L, Long::sum);
        }
        List<PaymentMethodStat> paymentBreakdown = revenueByMethod.entrySet().stream()
                .map(e -> new PaymentMethodStat(e.getKey(), e.getValue(), countByMethod.get(e.getKey())))
                .toList();

        BigDecimal creditAmountPaidTotal = transactions.stream()
                .filter(t -> "CREDIT".equals(t.paymentMethod()))
                .map(DailyTransactionDto::amountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditBalanceDueTotal = transactions.stream()
                .filter(t -> "CREDIT".equals(t.paymentMethod()))
                .map(DailyTransactionDto::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DailyReportDto(
                date,
                totalOpeningFloat, totalExpectedCash, totalCountedCash, totalVariance, allClosed,
                depositsTotal, withdrawalsTotal,
                grossSalesTotal, transactions.size(), paymentBreakdown,
                creditAmountPaidTotal, creditBalanceDueTotal,
                sessions, transactions);
    }

    private DailyTransactionDto toTransaction(Object[] r) {
        return new DailyTransactionDto(
                (UUID) r[0],
                String.valueOf(r[1]),
                toInstant(r[2]),
                r[3] != null ? String.valueOf(r[3]) : "—",
                String.valueOf(r[4]),
                String.valueOf(r[5]),
                r[6] != null ? String.valueOf(r[6]) : null,
                toBigDecimal(r[7]),
                toBigDecimal(r[8]),
                toBigDecimal(r[9]),
                toBigDecimal(r[10]),
                toBigDecimal(r[11]),
                toBigDecimal(r[12]),
                String.valueOf(r[13]),
                String.valueOf(r[14]));
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
}
