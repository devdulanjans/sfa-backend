package com.sfa.service;

import com.sfa.dto.tracking.TrackingDtos.*;
import com.sfa.entity.Customer;
import com.sfa.entity.GeofenceVisit;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Field tracking (V114): stores the GPS points a sales rep's phone uploads, derives geofence
 * visits from them (rep stayed within the geofence radius of a customer for the minimum dwell
 * time), and serves the admin Field Tracking views.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackingService {

    /** A gap this long between points ends an open visit — the rep may have left unseen. */
    private static final Duration VISIT_GAP = Duration.ofMinutes(20);

    private final SystemSettingService settings;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @PersistenceContext
    private EntityManager em;

    // ── Mobile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TrackingConfig config() {
        return new TrackingConfig(
                settings.getBool("location_tracking_enabled", true),
                Math.max(5, settings.getInt("location_tracking_interval_seconds", 30)),
                Math.max(0, settings.getInt("location_tracking_distance_meters", 25)));
    }

    /** Stores a batch of points (duplicates ignored) and updates the rep's geofence visits. */
    public UploadResult upload(UUID repId, UploadRequest req) {
        if (!settings.getBool("location_tracking_enabled", true)) {
            return new UploadResult(req.points().size(), 0);
        }
        UUID tenantId = TenantContext.getTenantId();
        Instant now = Instant.now();
        List<PointRequest> sorted = req.points().stream()
                .filter(p -> p.recordedAt() != null && !p.recordedAt().isAfter(now.plusSeconds(300)))
                .filter(p -> valid(p.latitude(), -90, 90) && valid(p.longitude(), -180, 180))
                .sorted(Comparator.comparing(PointRequest::recordedAt))
                .toList();

        List<PointRequest> accepted = new ArrayList<>();
        for (PointRequest p : sorted) {
            int n = em.createNativeQuery("""
                    INSERT INTO location_points (sales_rep_id, tenant_id, recorded_at, latitude, longitude,
                                                 accuracy_m, speed_mps, heading, battery_pct, is_mock)
                    VALUES (:rep, :tenant, :t, :lat, :lng, :acc, :speed, :heading, :battery, :mock)
                    ON CONFLICT (sales_rep_id, recorded_at) DO NOTHING
                    """)
                    .setParameter("rep", repId)
                    .setParameter("tenant", tenantId)
                    .setParameter("t", Timestamp.from(p.recordedAt()))
                    .setParameter("lat", p.latitude())
                    .setParameter("lng", p.longitude())
                    .setParameter("acc", p.accuracy())
                    .setParameter("speed", p.speed())
                    .setParameter("heading", p.heading())
                    .setParameter("battery", p.battery() != null ? p.battery().shortValue() : null)
                    .setParameter("mock", Boolean.TRUE.equals(p.isMock()))
                    .executeUpdate();
            if (n > 0) accepted.add(p);
        }
        if (!accepted.isEmpty()) {
            processGeofences(repId, tenantId, accepted);
        }
        return new UploadResult(req.points().size(), accepted.size());
    }

    // ── Geofencing ─────────────────────────────────────────────────────────────

    private record Site(UUID id, double lat, double lng) {}

    private void processGeofences(UUID repId, UUID tenantId, List<PointRequest> points) {
        int radius = Math.max(10, settings.getInt("geofence_radius_meters", 100));
        Duration minDwell = Duration.ofMinutes(Math.max(0, settings.getInt("geofence_min_dwell_minutes", 2)));
        List<Site> sites = customerSites(repId);
        if (sites.isEmpty()) return;

        GeofenceVisit open = em.createQuery(
                        "SELECT v FROM GeofenceVisit v WHERE v.salesRepId = :rep AND v.status = 'OPEN' ORDER BY v.arrivedAt DESC",
                        GeofenceVisit.class)
                .setParameter("rep", repId).setMaxResults(1)
                .getResultStream().findFirst().orElse(null);

        for (PointRequest p : points) {
            // A late (offline) point older than the visit we're tracking can't reorder history.
            if (open != null && p.recordedAt().isBefore(open.getDepartedAt())) continue;
            // A very inaccurate fix can't place the rep at a specific customer.
            if (p.accuracy() != null && p.accuracy() > Math.max(2 * radius, 150)) continue;

            if (open != null && Duration.between(open.getDepartedAt(), p.recordedAt()).compareTo(VISIT_GAP) > 0) {
                close(open, minDwell);
                open = null;
            }

            double lat = p.latitude().doubleValue(), lng = p.longitude().doubleValue();
            Site nearest = null;
            double best = Double.MAX_VALUE;
            for (Site s : sites) {
                double d = haversineM(lat, lng, s.lat(), s.lng());
                if (d <= radius && d < best) { best = d; nearest = s; }
            }

            if (nearest != null && open != null && open.getCustomer().getId().equals(nearest.id())) {
                open.setDepartedAt(p.recordedAt());
                open.setPointCount(open.getPointCount() + 1);
                if (open.getMinDistanceM() == null || best < open.getMinDistanceM()) open.setMinDistanceM((float) best);
            } else {
                if (open != null) {
                    close(open, minDwell);
                    open = null;
                }
                if (nearest != null) {
                    open = GeofenceVisit.builder()
                            .salesRepId(repId)
                            .customer(em.getReference(Customer.class, nearest.id()))
                            .tenantId(tenantId)
                            .arrivedAt(p.recordedAt())
                            .departedAt(p.recordedAt())
                            .pointCount(1)
                            .minDistanceM((float) best)
                            .build();
                    em.persist(open);
                }
            }
        }
    }

    /** Ends a visit — kept as CLOSED if it lasted the minimum dwell time, else dropped as a drive-by. */
    private void close(GeofenceVisit v, Duration minDwell) {
        if (Duration.between(v.getArrivedAt(), v.getDepartedAt()).compareTo(minDwell) >= 0) {
            v.setStatus("CLOSED");
        } else {
            em.remove(v);
        }
    }

    /** Customers with a saved location in any channel the rep belongs to. */
    @SuppressWarnings("unchecked")
    private List<Site> customerSites(UUID repId) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DISTINCT c.id, c.latitude, c.longitude FROM customers c
                JOIN customer_channels cc ON cc.customer_id = c.id
                JOIN user_tenants ut ON ut.tenant_id = cc.tenant_id AND ut.user_id = :rep
                WHERE c.deleted_at IS NULL AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL
                """).setParameter("rep", repId).getResultList();
        return rows.stream()
                .map(r -> new Site((UUID) r[0], ((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue()))
                .toList();
    }

    // ── Admin ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RepDayDto repDay(UUID repId, LocalDate date) {
        User rep = requireVisibleRep(repId);
        Instant from = startOf(date), to = startOf(date.plusDays(1));
        List<PointDto> points = points(repId, from, to);

        List<GeofenceVisit> visits = em.createQuery("""
                SELECT v FROM GeofenceVisit v JOIN FETCH v.customer
                WHERE v.salesRepId = :rep AND v.arrivedAt >= :from AND v.arrivedAt < :to
                ORDER BY v.arrivedAt""", GeofenceVisit.class)
                .setParameter("rep", repId).setParameter("from", from).setParameter("to", to)
                .getResultList();
        Map<UUID, List<String>> orders = ordersByCustomer(repId, from, to);
        Map<UUID, String> notes = visitNotesByCustomer(repId, date);
        int radius = settings.getInt("geofence_radius_meters", 100);
        Duration minDwell = Duration.ofMinutes(settings.getInt("geofence_min_dwell_minutes", 2));

        List<VisitDto> visitDtos = visits.stream()
                // An OPEN visit that hasn't reached the minimum dwell yet isn't a visit (yet).
                .filter(v -> !"OPEN".equals(v.getStatus())
                        || Duration.between(v.getArrivedAt(), v.getDepartedAt()).compareTo(minDwell) >= 0)
                .map(v -> {
                    Customer c = v.getCustomer();
                    return new VisitDto(v.getId(), c.getId(), c.getName(), c.getCustomerCode(),
                            c.getLatitude(), c.getLongitude(), v.getArrivedAt(), v.getDepartedAt(),
                            Duration.between(v.getArrivedAt(), v.getDepartedAt()).toMinutes(),
                            v.getStatus(), v.getMinDistanceM(),
                            orders.getOrDefault(c.getId(), List.of()), notes.get(c.getId()));
                }).toList();

        return new RepDayDto(rep.getId(), name(rep), date, downsample(points, 4000), visitDtos,
                round2(distanceKm(points, radius)),
                points.isEmpty() ? null : points.get(0).t(),
                points.isEmpty() ? null : points.get(points.size() - 1).t(),
                points.size());
    }

    /** Latest position of every visible sales rep seen in the last 24 hours. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<LiveRepDto> live() {
        Set<UUID> reps = visibleRepIds();
        if (reps.isEmpty()) return List.of();
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DISTINCT ON (p.sales_rep_id) p.sales_rep_id, p.recorded_at, p.latitude, p.longitude,
                       p.accuracy_m, p.speed_mps, p.battery_pct
                FROM location_points p
                WHERE p.sales_rep_id IN (:reps) AND p.recorded_at > :since
                ORDER BY p.sales_rep_id, p.recorded_at DESC
                """).setParameter("reps", reps).setParameter("since", Timestamp.from(Instant.now().minus(Duration.ofHours(24))))
                .getResultList();
        Map<UUID, User> users = userRepository.findAllById(reps).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, String> inside = new HashMap<>();
        em.createQuery("SELECT v FROM GeofenceVisit v JOIN FETCH v.customer WHERE v.status = 'OPEN' AND v.salesRepId IN :reps",
                        GeofenceVisit.class)
                .setParameter("reps", reps).getResultList()
                .forEach(v -> inside.put(v.getSalesRepId(), v.getCustomer().getName()));
        return rows.stream().map(r -> {
            UUID id = (UUID) r[0];
            User u = users.get(id);
            return new LiveRepDto(id, u != null ? name(u) : null, u != null ? u.getUsername() : null,
                    toInstant(r[1]), (BigDecimal) r[2], (BigDecimal) r[3],
                    r[4] != null ? ((Number) r[4]).floatValue() : null,
                    r[5] != null ? ((Number) r[5]).floatValue() : null,
                    r[6] != null ? ((Number) r[6]).intValue() : null,
                    inside.get(id));
        }).sorted(Comparator.comparing(LiveRepDto::lastSeen).reversed()).toList();
    }

    /** Per-rep totals for a day, for every visible sales rep (reps with no points included). */
    @Transactional(readOnly = true)
    public List<RepSummaryDto> summary(LocalDate date) {
        Instant from = startOf(date), to = startOf(date.plusDays(1));
        int radius = settings.getInt("geofence_radius_meters", 100);
        Duration minDwell = Duration.ofMinutes(settings.getInt("geofence_min_dwell_minutes", 2));
        List<RepSummaryDto> out = new ArrayList<>();
        for (User rep : userRepository.findAllById(visibleRepIds())) {
            List<PointDto> pts = points(rep.getId(), from, to);
            long visits = em.createQuery("""
                    SELECT v FROM GeofenceVisit v WHERE v.salesRepId = :rep AND v.arrivedAt >= :from AND v.arrivedAt < :to""",
                    GeofenceVisit.class)
                    .setParameter("rep", rep.getId()).setParameter("from", from).setParameter("to", to)
                    .getResultStream()
                    .filter(v -> Duration.between(v.getArrivedAt(), v.getDepartedAt()).compareTo(minDwell) >= 0)
                    .count();
            out.add(new RepSummaryDto(rep.getId(), name(rep), pts.size(), round2(distanceKm(pts, radius)),
                    pts.isEmpty() ? null : pts.get(0).t(), pts.isEmpty() ? null : pts.get(pts.size() - 1).t(),
                    (int) visits));
        }
        out.sort(Comparator.comparing(RepSummaryDto::salesRepName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Sales reps the caller may see: every rep for an unscoped/SUPER_ADMIN caller, else reps
     *  sharing one of the caller's visible channels. */
    @SuppressWarnings("unchecked")
    private Set<UUID> visibleRepIds() {
        List<User> reps = userRepository.findActiveByRoleName(Role.SALES_REP, User.UserStatus.ACTIVE);
        if (TenantContext.isUnscoped()) return reps.stream().map(User::getId).collect(Collectors.toSet());
        Set<UUID> visible = TenantContext.getVisibleTenantIds();
        Set<UUID> out = new HashSet<>();
        for (User r : reps) {
            boolean shares = tenantRepository.findByUserId(r.getId()).stream()
                    .map(Tenant::getId).anyMatch(visible::contains);
            if (shares) out.add(r.getId());
        }
        return out;
    }

    private User requireVisibleRep(UUID repId) {
        User rep = userRepository.findById(repId).orElseThrow(() -> new ResourceNotFoundException("User", repId));
        if (!visibleRepIds().contains(repId)) {
            throw new BusinessException("You don't have access to this sales rep's location.");
        }
        return rep;
    }

    @SuppressWarnings("unchecked")
    private List<PointDto> points(UUID repId, Instant from, Instant to) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT recorded_at, latitude, longitude, accuracy_m, speed_mps, battery_pct, is_mock
                FROM location_points WHERE sales_rep_id = :rep AND recorded_at >= :from AND recorded_at < :to
                ORDER BY recorded_at""")
                .setParameter("rep", repId).setParameter("from", Timestamp.from(from)).setParameter("to", Timestamp.from(to))
                .getResultList();
        return rows.stream().map(r -> new PointDto(toInstant(r[0]), (BigDecimal) r[1], (BigDecimal) r[2],
                r[3] != null ? ((Number) r[3]).floatValue() : null,
                r[4] != null ? ((Number) r[4]).floatValue() : null,
                r[5] != null ? ((Number) r[5]).intValue() : null,
                Boolean.TRUE.equals(r[6]))).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<String>> ordersByCustomer(UUID repId, Instant from, Instant to) {
        List<Object[]> rows = em.createQuery("""
                SELECT o.customer.id, o.orderNumber FROM Order o WHERE o.salesRep.id = :rep
                AND o.orderDate >= :from AND o.orderDate < :to AND o.status <> :cancelled
                ORDER BY o.orderDate""")
                .setParameter("rep", repId).setParameter("from", from).setParameter("to", to)
                .setParameter("cancelled", com.sfa.entity.Order.OrderStatus.CANCELLED).getResultList();
        Map<UUID, List<String>> m = new HashMap<>();
        rows.forEach(r -> m.computeIfAbsent((UUID) r[0], k -> new ArrayList<>()).add((String) r[1]));
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> visitNotesByCustomer(UUID repId, LocalDate date) {
        List<Object[]> rows = em.createQuery(
                        "SELECT v.customer.id, v.reasonLabel FROM VisitNote v WHERE v.salesRep.id = :rep AND v.visitDate = :d")
                .setParameter("rep", repId).setParameter("d", date).getResultList();
        Map<UUID, String> m = new HashMap<>();
        rows.forEach(r -> m.put((UUID) r[0], (String) r[1]));
        return m;
    }

    /** Distance along the path, ignoring fixes too inaccurate to trust. */
    private static double distanceKm(List<PointDto> pts, int radius) {
        double m = 0;
        PointDto prev = null;
        for (PointDto p : pts) {
            if (p.accuracy() != null && p.accuracy() > Math.max(2 * radius, 150)) continue;
            if (prev != null) {
                m += haversineM(prev.lat().doubleValue(), prev.lng().doubleValue(), p.lat().doubleValue(), p.lng().doubleValue());
            }
            prev = p;
        }
        return m / 1000.0;
    }

    private static List<PointDto> downsample(List<PointDto> pts, int max) {
        if (pts.size() <= max) return pts;
        double step = (double) pts.size() / max;
        List<PointDto> out = new ArrayList<>(max + 1);
        for (int i = 0; i < max; i++) out.add(pts.get((int) Math.floor(i * step)));
        out.add(pts.get(pts.size() - 1));
        return out;
    }

    static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static boolean valid(BigDecimal v, double min, double max) {
        return v != null && v.doubleValue() >= min && v.doubleValue() <= max;
    }

    private static Instant startOf(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String name(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }
}
