package com.sfa.repository;

import com.sfa.entity.SalesRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SalesRouteRepository extends JpaRepository<SalesRoute, UUID> {

    /** @param status null → any status. Channel-scoped by the tenant filter. */
    @Query(
        value = """
            SELECT r FROM SalesRoute r WHERE
            (:status IS NULL OR r.status = :status) AND (
            LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY r.name
        """,
        countQuery = """
            SELECT COUNT(r) FROM SalesRoute r WHERE
            (:status IS NULL OR r.status = :status) AND (
            LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<SalesRoute> search(@Param("query") String query,
                            @Param("status") SalesRoute.RouteStatus status,
                            Pageable pageable);

    List<SalesRoute> findByStatusOrderByName(SalesRoute.RouteStatus status);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    /** Stop counts per route, for list views — avoids loading every route's customer list. */
    @Query(value = "SELECT route_id, COUNT(*) FROM sales_route_customers WHERE route_id IN (:ids) GROUP BY route_id",
           nativeQuery = true)
    List<Object[]> countStops(@Param("ids") List<UUID> ids);

    /** Open route suggestions per route (V113). */
    @Query(value = "SELECT suggested_route_id, COUNT(*) FROM customers WHERE suggested_route_id IN (:ids) AND deleted_at IS NULL GROUP BY suggested_route_id",
           nativeQuery = true)
    List<Object[]> countSuggestions(@Param("ids") List<UUID> ids);
}
