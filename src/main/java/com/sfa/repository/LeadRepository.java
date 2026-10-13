package com.sfa.repository;

import com.sfa.entity.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    /**
     * @param status    null → any status
     * @param createdBy null → leads by anyone (reviewers); otherwise only that user's own leads
     */
    @Query(
        value = """
            SELECT l FROM Lead l LEFT JOIN FETCH l.createdBy LEFT JOIN FETCH l.convertedCustomer WHERE
            (:status IS NULL OR l.status = :status) AND
            (:createdBy IS NULL OR l.createdBy.id = :createdBy) AND (
            LOWER(l.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(l.leadNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(l.location, '')) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY l.createdAt DESC
        """,
        countQuery = """
            SELECT COUNT(l) FROM Lead l WHERE
            (:status IS NULL OR l.status = :status) AND
            (:createdBy IS NULL OR l.createdBy.id = :createdBy) AND (
            LOWER(l.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(l.leadNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(l.location, '')) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Lead> search(@Param("query") String query,
                      @Param("status") Lead.LeadStatus status,
                      @Param("createdBy") UUID createdBy,
                      Pageable pageable);
}
