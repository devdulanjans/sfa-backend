package com.sfa.repository;

import com.sfa.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerCode(String customerCode);
    Page<Customer> findByStatus(Customer.CustomerStatus status, Pageable pageable);

    /** One row per (customer, assigned product) pair — used to bulk-populate
     *  CustomerDto.assignedProductIds for a page of customers without N+1
     *  queries or touching the lazy assignedProducts collection per-row. */
    interface CustomerProductIdRow {
        UUID getCustomerId();
        UUID getProductId();
    }

    @Query("""
        SELECT c.id AS customerId, p.id AS productId
        FROM Customer c JOIN c.assignedProducts p
        WHERE c.id IN :customerIds
    """)
    List<CustomerProductIdRow> findAssignedProductIdsForCustomers(@Param("customerIds") Collection<UUID> customerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    @Query(
        value = """
            SELECT c FROM Customer c LEFT JOIN FETCH c.category WHERE
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY LOWER(c.name) ASC
        """,
        countQuery = """
            SELECT COUNT(c) FROM Customer c WHERE
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%'))
        """
    )
    Page<Customer> search(@Param("query") String query, Pageable pageable);

    // Filtered search — used when the caller only has access to a specific set of customers
    @Query(
        value = """
            SELECT c FROM Customer c LEFT JOIN FETCH c.category WHERE
            c.id IN :ids AND (
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY LOWER(c.name) ASC
        """,
        countQuery = """
            SELECT COUNT(c) FROM Customer c WHERE
            c.id IN :ids AND (
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%')))
        """
    )
    Page<Customer> searchWithinIds(@Param("query") String query, @Param("ids") Set<UUID> ids, Pageable pageable);

    // POS-generated customers only — used by the /pos billing dropdown and the admin "Customers" page under POS
    @Query(
        value = """
            SELECT c FROM Customer c LEFT JOIN FETCH c.category WHERE
            c.source = 'POS' AND (
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY LOWER(c.name) ASC
        """,
        countQuery = """
            SELECT COUNT(c) FROM Customer c WHERE
            c.source = 'POS' AND (
            LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')))
        """
    )
    Page<Customer> searchPosCustomers(@Param("query") String query, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.updatedAt >= :since")
    List<Customer> findUpdatedSince(@Param("since") Instant since);

    // Delta sync filtered to a specific set of customers
    @Query("SELECT c FROM Customer c WHERE c.id IN :ids AND c.updatedAt >= :since")
    List<Customer> findByIdsUpdatedSince(@Param("ids") Set<UUID> ids, @Param("since") Instant since);

    // Sync variants that eagerly load assignedProducts so CustomerDto includes product IDs.
    // POS-generated customers are excluded — they exist only for POS billing, not for mobile sales reps.
    @EntityGraph(attributePaths = "assignedProducts")
    @Query("SELECT c FROM Customer c WHERE c.updatedAt >= :since AND c.source <> 'POS'")
    List<Customer> findUpdatedSinceWithProducts(@Param("since") Instant since);

    @EntityGraph(attributePaths = "assignedProducts")
    @Query("SELECT c FROM Customer c WHERE c.id IN :ids AND c.updatedAt >= :since AND c.source <> 'POS'")
    List<Customer> findByIdsUpdatedSinceWithProducts(@Param("ids") Set<UUID> ids, @Param("since") Instant since);
}
