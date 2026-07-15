package com.sfa.repository;

import com.sfa.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("""
        SELECT e FROM Expense e LEFT JOIN FETCH e.recordedByUser
        WHERE (:category IS NULL OR e.category = :category)
          AND e.expenseDate >= COALESCE(:dateFrom, e.expenseDate)
          AND e.expenseDate <= COALESCE(:dateTo, e.expenseDate)
        ORDER BY e.expenseDate DESC, e.createdAt DESC
        """)
    Page<Expense> findFiltered(
            @Param("category") Expense.Category category,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.expenseDate BETWEEN :dateFrom AND :dateTo")
    BigDecimal sumBetween(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.expenseDate BETWEEN :dateFrom AND :dateTo GROUP BY e.category")
    List<Object[]> sumByCategoryBetween(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    @Query("""
        SELECT e FROM Expense e LEFT JOIN FETCH e.recordedByUser
        WHERE e.expenseDate BETWEEN :dateFrom AND :dateTo
        ORDER BY e.expenseDate ASC, e.createdAt ASC
        """)
    List<Expense> findAllBetween(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
