package com.sfa.service;

import com.sfa.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * In-app alerts for a channel's owners and managers (V122) — the bell in the admin panel.
 * One row per event per channel; each user's read state is kept separately. A channel session
 * sees its own channel's alerts; the SUPER_ADMIN platform view sees every channel's.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminNotificationService {

    public record NotificationDto(UUID id, String type, String title, String message, String link,
                                  Instant createdAt, String createdByName, String channelName, boolean read) {}

    public record Feed(long unread, List<NotificationDto> items) {}

    @PersistenceContext
    private EntityManager em;

    /** Raises an alert for one channel. Runs in the caller's transaction, so it only exists if that commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(UUID tenantId, String type, String title, String message, String link, UUID createdBy) {
        if (tenantId == null) return;
        em.createNativeQuery("""
                INSERT INTO admin_notifications (tenant_id, type, title, message, link, created_by)
                VALUES (:t, :type, :title, :msg, :link, :by)
                """)
                .setParameter("t", tenantId).setParameter("type", type)
                .setParameter("title", cut(title, 200)).setParameter("msg", cut(message, 1000))
                .setParameter("link", link).setParameter("by", createdBy)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Feed feed(UUID userId, int limit) {
        String scope = scopeClause();
        Query q = em.createNativeQuery("""
                SELECT n.id, n.type, n.title, n.message, n.link, n.created_at,
                       COALESCE(u.full_name, u.username), t.name,
                       (r.user_id IS NOT NULL)
                FROM admin_notifications n
                JOIN tenants t ON t.id = n.tenant_id
                LEFT JOIN users u ON u.id = n.created_by
                LEFT JOIN admin_notification_reads r ON r.notification_id = n.id AND r.user_id = :u
                WHERE 1 = 1 """ + scope + """
                 ORDER BY n.created_at DESC LIMIT :lim""")
                .setParameter("u", userId).setParameter("lim", Math.max(1, Math.min(limit, 100)));
        bindScope(q);
        List<NotificationDto> items = ((List<Object[]>) q.getResultList()).stream()
                .map(r -> new NotificationDto((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                        (String) r[4], toInstant(r[5]), (String) r[6], (String) r[7], Boolean.TRUE.equals(r[8])))
                .toList();

        Query c = em.createNativeQuery("""
                SELECT COUNT(*) FROM admin_notifications n
                WHERE NOT EXISTS (SELECT 1 FROM admin_notification_reads r
                                  WHERE r.notification_id = n.id AND r.user_id = :u)""" + scope)
                .setParameter("u", userId);
        bindScope(c);
        return new Feed(((Number) c.getSingleResult()).longValue(), items);
    }

    public void markRead(UUID notificationId, UUID userId) {
        Query q = em.createNativeQuery("""
                INSERT INTO admin_notification_reads (notification_id, user_id)
                SELECT n.id, :u FROM admin_notifications n WHERE n.id = :id""" + scopeClause() + """
                 ON CONFLICT DO NOTHING""")
                .setParameter("u", userId).setParameter("id", notificationId);
        bindScope(q);
        q.executeUpdate();
    }

    public void markAllRead(UUID userId) {
        Query q = em.createNativeQuery("""
                INSERT INTO admin_notification_reads (notification_id, user_id)
                SELECT n.id, :u FROM admin_notifications n WHERE 1 = 1""" + scopeClause() + """
                 ON CONFLICT DO NOTHING""")
                .setParameter("u", userId);
        bindScope(q);
        q.executeUpdate();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Channel sessions see their channel only; a pending session sees nothing; platform view sees all. */
    private static String scopeClause() {
        if (TenantContext.isUnscoped()) return " ";
        return " AND n.tenant_id = :tenant ";
    }

    private static void bindScope(Query q) {
        if (TenantContext.isUnscoped()) return;
        UUID t = TenantContext.getTenantId();
        // Pending (no channel picked): bind an id that matches nothing.
        q.setParameter("tenant", t != null ? t : new UUID(0, 0));
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp ts) return ts.toInstant();
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        return null;
    }
}
