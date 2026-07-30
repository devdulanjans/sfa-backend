package com.sfa.repository;

import com.sfa.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    // Date bounds use COALESCE, not "(:param IS NULL OR ...)" — see JournalEntryLineRepository
    // for why a date parameter appearing only in an IS NULL check makes Postgres unable to infer
    // its type (SQLState 42P18). sourceType is fine as IS-NULL-OR (non-temporal, same as the
    // existing Expense.category filter pattern in ExpenseRepository.findFiltered).
    @Query("""
        SELECT je FROM JournalEntry je
        WHERE je.entryDate >= COALESCE(:dateFrom, je.entryDate)
          AND je.entryDate <= COALESCE(:dateTo, je.entryDate)
          AND (:sourceType IS NULL OR je.sourceType = :sourceType)
        ORDER BY je.entryDate DESC, je.createdAt DESC
        """)
    Page<JournalEntry> findFiltered(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("sourceType") JournalEntry.SourceType sourceType,
            Pageable pageable);
}
