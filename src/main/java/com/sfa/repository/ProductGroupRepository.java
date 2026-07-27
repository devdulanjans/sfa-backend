package com.sfa.repository;

import com.sfa.entity.ProductGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID> {

    /** One row per group in {@code groupIds}, with its member count — bulk-loaded to avoid
     *  N+1 queries when rendering a page of groups. */
    interface GroupMemberCountRow {
        UUID getGroupId();
        Long getMemberCount();
    }

    @Query("""
        SELECT pg.id AS groupId, COUNT(m) AS memberCount
        FROM ProductGroup pg LEFT JOIN pg.members m
        WHERE pg.id IN :groupIds
        GROUP BY pg.id
    """)
    List<GroupMemberCountRow> countMembersForGroups(@Param("groupIds") Collection<UUID> groupIds);

    /** Groups that currently contain the given product — backs the reverse
     *  Product → Product Groups picker on the product edit page. */
    @Query("SELECT pg FROM ProductGroup pg JOIN pg.members m WHERE m.id = :productId")
    List<ProductGroup> findAllContainingProduct(@Param("productId") UUID productId);
}
