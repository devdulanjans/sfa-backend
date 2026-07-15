package com.sfa.service;

import com.sfa.dto.DrawerSessionDto;
import com.sfa.entity.CashMovement;
import com.sfa.entity.DrawerSession;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CashMovementRepository;
import com.sfa.repository.DrawerSessionRepository;
import com.sfa.repository.PosSaleRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DrawerService {

    private final DrawerSessionRepository drawerSessionRepo;
    private final CashMovementRepository  cashMovementRepo;
    private final PosSaleRepository       posSaleRepo;
    private final UserRepository          userRepo;

    public DrawerSessionDto openSession(UUID cashierId, BigDecimal openingFloat, String notes) {
        if (drawerSessionRepo.existsByCashierIdAndStatus(cashierId, DrawerSession.Status.OPEN)) {
            throw new BusinessException("You already have an open drawer session");
        }
        if (openingFloat == null || openingFloat.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Opening float must be zero or greater");
        }
        User cashier = userRepo.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", cashierId));

        DrawerSession session = DrawerSession.builder()
                .cashier(cashier)
                .openingFloat(openingFloat)
                .openedAt(Instant.now())
                .openingNotes(notes)
                .build();
        DrawerSession saved = drawerSessionRepo.save(session);
        return DrawerSessionDto.from(saved, openingFloat);
    }

    @Transactional(readOnly = true)
    public DrawerSessionDto getCurrentSession(UUID cashierId) {
        return drawerSessionRepo.findByCashierIdAndStatus(cashierId, DrawerSession.Status.OPEN)
                .map(s -> DrawerSessionDto.from(s, computeExpectedCash(s, Instant.now())))
                .orElse(null);
    }

    public DrawerSessionDto recordMovement(UUID sessionId, String typeStr, BigDecimal amount, String notes, UUID recordedBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be greater than zero");
        }
        CashMovement.Type type;
        try {
            type = CashMovement.Type.valueOf(typeStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Invalid movement type: " + typeStr);
        }

        DrawerSession session = drawerSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("DrawerSession", sessionId));
        if (session.getStatus() != DrawerSession.Status.OPEN) {
            throw new BusinessException("This drawer session is already closed");
        }

        BigDecimal expected = computeExpectedCash(session, Instant.now());
        if (type == CashMovement.Type.WITHDRAWAL && amount.compareTo(expected) > 0) {
            throw new BusinessException("Withdrawal (LKR " + amount + ") exceeds the current drawer balance (LKR " + expected + ")");
        }

        cashMovementRepo.save(CashMovement.builder()
                .session(session)
                .type(type)
                .amount(amount)
                .notes(notes)
                .recordedBy(recordedBy)
                .build());

        BigDecimal newExpected = computeExpectedCash(session, Instant.now());
        return DrawerSessionDto.from(session, newExpected);
    }

    public DrawerSessionDto closeSession(UUID sessionId, BigDecimal countedCash, String notes) {
        if (countedCash == null || countedCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Counted cash must be zero or greater");
        }
        DrawerSession session = drawerSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("DrawerSession", sessionId));
        if (session.getStatus() != DrawerSession.Status.OPEN) {
            throw new BusinessException("This drawer session is already closed");
        }

        Instant closedAt = Instant.now();
        BigDecimal expected = computeExpectedCash(session, closedAt);

        session.setClosedAt(closedAt);
        session.setExpectedCash(expected);
        session.setCountedCash(countedCash);
        session.setVariance(countedCash.subtract(expected));
        session.setClosingNotes(notes);
        session.setStatus(DrawerSession.Status.CLOSED);

        DrawerSession saved = drawerSessionRepo.save(session);
        return DrawerSessionDto.from(saved, expected);
    }

    @Transactional(readOnly = true)
    public Page<DrawerSessionDto> listSessions(UUID cashierId, String statusStr, Instant dateFrom, Instant dateTo, Pageable pageable) {
        DrawerSession.Status status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try { status = DrawerSession.Status.valueOf(statusStr); } catch (IllegalArgumentException ignored) {}
        }
        return drawerSessionRepo.findSessions(cashierId, status, dateFrom, dateTo, pageable)
                .map(s -> DrawerSessionDto.from(s,
                        s.getStatus() == DrawerSession.Status.OPEN ? computeExpectedCash(s, Instant.now()) : s.getExpectedCash()));
    }

    /** Opening float + cash sales by this cashier during the session window + deposits − withdrawals. */
    private BigDecimal computeExpectedCash(DrawerSession session, Instant asOf) {
        BigDecimal cashSales = posSaleRepo.sumCashSalesForCashier(session.getCashier().getId(), session.getOpenedAt(), asOf);
        BigDecimal deposits    = cashMovementRepo.sumBySessionAndType(session.getId(), CashMovement.Type.DEPOSIT);
        BigDecimal withdrawals = cashMovementRepo.sumBySessionAndType(session.getId(), CashMovement.Type.WITHDRAWAL);
        return session.getOpeningFloat().add(cashSales).add(deposits).subtract(withdrawals);
    }
}
