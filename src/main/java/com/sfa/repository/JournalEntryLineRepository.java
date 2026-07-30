package com.sfa.repository;

import com.sfa.entity.JournalEntry;
import com.sfa.entity.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    List<JournalEntryLine> findByJournalEntryIdOrderByLineOrder(UUID journalEntryId);

    // Date params use entryDate <= COALESCE(:param, entryDate) rather than "(:param IS NULL OR ...)" —
    // when a bind parameter's only appearance in the SQL is a bare "? IS NULL" check, Postgres can't
    // determine its type during prepare/describe (SQLState 42P18), regardless of the value bound at
    // runtime. COALESCE gives the parameter a typed sibling (the date column) in the same expression,
    // and only needs the parameter once. Matches the existing convention in ExpenseRepository.findFiltered.
    @Query("""
        SELECT COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0)
        FROM JournalEntryLine l
        WHERE l.account.id = :accountId AND l.journalEntry.status = :status
          AND l.journalEntry.entryDate <= COALESCE(:asOf, l.journalEntry.entryDate)
        """)
    BigDecimal netDebitBalance(
            @Param("accountId") UUID accountId,
            @Param("status") JournalEntry.JournalStatus status,
            @Param("asOf") LocalDate asOf);

    /** Bounded on both ends — for period reports (Profit & Loss) rather than cumulative-to-date balances. */
    @Query("""
        SELECT COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0)
        FROM JournalEntryLine l
        WHERE l.account.id = :accountId AND l.journalEntry.status = :status
          AND l.journalEntry.entryDate >= COALESCE(:dateFrom, l.journalEntry.entryDate)
          AND l.journalEntry.entryDate <= COALESCE(:dateTo, l.journalEntry.entryDate)
        """)
    BigDecimal netDebitInRange(
            @Param("accountId") UUID accountId,
            @Param("status") JournalEntry.JournalStatus status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("""
        SELECT l FROM JournalEntryLine l
        JOIN FETCH l.journalEntry je
        JOIN FETCH l.account
        WHERE l.account.id = :accountId AND je.status = :status
          AND je.entryDate >= COALESCE(:dateFrom, je.entryDate)
          AND je.entryDate <= COALESCE(:dateTo, je.entryDate)
        ORDER BY je.entryDate ASC, je.createdAt ASC
        """)
    List<JournalEntryLine> findLedgerForAccount(
            @Param("accountId") UUID accountId,
            @Param("status") JournalEntry.JournalStatus status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
}
