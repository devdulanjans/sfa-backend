package com.sfa.repository;

import com.sfa.entity.CustomerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CustomerGroupRepository extends JpaRepository<CustomerGroup, UUID> {

    /** One row per group in {@code groupIds}, with its member count — bulk-loaded to avoid
     *  N+1 queries when rendering a page of groups (mirrors CustomerService.list()'s
     *  assignedProductIdsByCustomer bulk-load pattern). */
    interface GroupMemberCountRow {
        UUID getGroupId();
        Long getMemberCount();
    }

    @Query("""
        SELECT cg.id AS groupId, COUNT(m) AS memberCount
        FROM CustomerGroup cg LEFT JOIN cg.members m
        WHERE cg.id IN :groupIds
        GROUP BY cg.id
    """)
    List<GroupMemberCountRow> countMembersForGroups(@Param("groupIds") Collection<UUID> groupIds);

    /** Same as {@link #countMembersForGroups} but for assignedProducts, used by the list view's
     *  "Products" column. */
    interface GroupProductCountRow {
        UUID getGroupId();
        Long getProductCount();
    }

    @Query("""
        SELECT cg.id AS groupId, COUNT(p) AS productCount
        FROM CustomerGroup cg LEFT JOIN cg.assignedProducts p
        WHERE cg.id IN :groupIds
        GROUP BY cg.id
    """)
    List<GroupProductCountRow> countAssignedProductsForGroups(@Param("groupIds") Collection<UUID> groupIds);

    /** One row per (customer, product) pair reachable via a customer group's assignedProducts —
     *  used by CustomerService.toDtos to compute each customer's effectiveAssignedProductIds
     *  (their own direct assignments unioned with every group they belong to). */
    interface CustomerProductIdRow {
        UUID getCustomerId();
        UUID getProductId();
    }

    @Query("""
        SELECT m.id AS customerId, p.id AS productId
        FROM CustomerGroup cg JOIN cg.members m JOIN cg.assignedProducts p
        WHERE m.id IN :customerIds
    """)
    List<CustomerProductIdRow> findAssignedProductIdsForCustomers(@Param("customerIds") Collection<UUID> customerIds);
}
