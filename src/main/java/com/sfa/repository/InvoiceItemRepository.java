package com.sfa.repository;

import com.sfa.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    /**
     * Discount, tax and line total already billed for an order line by its invoices that
     * weren't cancelled — one row of three sums. The last invoice of a line bills exactly the
     * rest, so an order invoiced in parts adds up to the order to the cent.
     */
    @Query("""
        SELECT COALESCE(SUM(ii.discountAmount), 0), COALESCE(SUM(ii.taxAmount), 0), COALESCE(SUM(ii.lineTotal), 0)
        FROM InvoiceItem ii
        WHERE ii.orderItem.id = :orderItemId
          AND ii.invoice.status <> com.sfa.entity.Invoice$InvoiceStatus.CANCELLED
        """)
    List<Object[]> billedSoFar(@Param("orderItemId") UUID orderItemId);
}
