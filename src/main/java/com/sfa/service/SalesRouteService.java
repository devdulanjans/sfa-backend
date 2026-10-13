package com.sfa.service;

import com.sfa.dto.route.SalesRouteDtos.*;
import com.sfa.dto.route.VisitNoteDtos;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.*;
import com.sfa.security.TenantContext;
import com.sfa.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Sales routes (ordered customer stops) and their per-day assignment to sales reps.
 * Routes, and their assignments, belong to one channel; every stop must be a customer of that
 * same channel, and a route can only be assigned to a rep who is a member of it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SalesRouteService {

    private final SalesRouteRepository routeRepository;
    private final SalesRouteAssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccessService tenantAccessService;
    private final AuditLogService auditLogService;
    private final SystemSettingService systemSettingService;
    private final VisitNoteRepository visitNoteRepository;

    @PersistenceContext
    private EntityManager em;

    // ── Routes ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RouteSummaryDto> search(String query, String status, int page, int size) {
        SalesRoute.RouteStatus st = parseStatus(status, true);
        Page<SalesRoute> routes = routeRepository.search(query == null ? "" : query.trim(), st,
                PageRequest.of(page, size));
        Map<UUID, Integer> counts = stopCounts(routes.getContent());
        Map<UUID, Integer> suggestions = suggestionCounts(routes.getContent());
        return routes.map(r -> RouteSummaryDto.from(r, counts.getOrDefault(r.getId(), 0),
                suggestions.getOrDefault(r.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public List<RouteSummaryDto> activeRoutes() {
        List<SalesRoute> routes = routeRepository.findByStatusOrderByName(SalesRoute.RouteStatus.ACTIVE);
        Map<UUID, Integer> counts = stopCounts(routes);
        Map<UUID, Integer> suggestions = suggestionCounts(routes);
        return routes.stream().map(r -> RouteSummaryDto.from(r, counts.getOrDefault(r.getId(), 0),
                suggestions.getOrDefault(r.getId(), 0))).toList();
    }

    @Transactional(readOnly = true)
    public RouteDetailDto get(UUID id) {
        return RouteDetailDto.from(findRoute(id));
    }

    public RouteDetailDto create(SaveRouteRequest req, UUID actingUserId) {
        Optional<Tenant> explicit = tenantAccessService.resolveExplicitTenant(req.tenantId(), actingUserId, "route");
        UUID tenantId = explicit.map(Tenant::getId).orElseGet(TenantContext::getTenantId);
        if (tenantId == null) {
            throw new BusinessException("Select a channel for this route.");
        }
        String name = req.name().trim();
        if (routeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new BusinessException("A route named \"" + name + "\" already exists in this channel.");
        }

        SalesRoute route = new SalesRoute();
        explicit.ifPresent(route::setTenant);
        route.setName(name);
        route.setDescription(blankToNull(req.description()));
        route.setStatus(parseStatus(req.status(), false));
        route.setCreatedBy(userRepository.getReferenceById(actingUserId));
        route.getCustomers().addAll(resolveStops(req.customerIds(), tenantId));

        SalesRoute saved = routeRepository.save(route);
        clearSuggestionsFulfilledBy(saved);
        auditLogService.log(actingUserId, "CREATE", "SALES_ROUTE", saved.getId(), null,
                Map.of("name", saved.getName(), "stops", saved.getCustomers().size()));
        return RouteDetailDto.from(saved);
    }

    public RouteDetailDto update(UUID id, SaveRouteRequest req, UUID actingUserId) {
        SalesRoute route = findRoute(id);
        UUID tenantId = route.getTenant().getId();
        String name = req.name().trim();
        if (routeRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw new BusinessException("A route named \"" + name + "\" already exists in this channel.");
        }
        route.setName(name);
        route.setDescription(blankToNull(req.description()));
        route.setStatus(parseStatus(req.status(), false));
        List<Customer> stops = resolveStops(req.customerIds(), tenantId);
        route.getCustomers().clear();
        route.getCustomers().addAll(stops);

        SalesRoute saved = routeRepository.save(route);
        clearSuggestionsFulfilledBy(saved);
        auditLogService.log(actingUserId, "UPDATE", "SALES_ROUTE", saved.getId(), null,
                Map.of("name", saved.getName(), "stops", saved.getCustomers().size(),
                       "status", saved.getStatus().name()));
        return RouteDetailDto.from(saved);
    }

    /** Only a route that was never assigned can be deleted — otherwise deactivate it, which
     *  keeps the assignment history intact. */
    public void delete(UUID id, UUID actingUserId) {
        SalesRoute route = findRoute(id);
        if (assignmentRepository.existsByRouteId(id)) {
            throw new BusinessException("This route has been assigned to sales reps. Set it to Inactive instead of deleting it.");
        }
        routeRepository.delete(route);
        auditLogService.log(actingUserId, "DELETE", "SALES_ROUTE", id, null, Map.of("name", route.getName()));
    }

    /** Customers of the caller's channel(s) with their map position — for the route editor and
     *  the Assign Route map. {@code tenantId} narrows an unscoped (SUPER_ADMIN) caller to one channel. */
    @Transactional(readOnly = true)
    public List<RouteCustomerDto> customerLocations(UUID tenantId) {
        String jpql = "SELECT c FROM Customer c WHERE c.deletedAt IS NULL"
                + (tenantId != null ? " AND :tenantId MEMBER OF c.channelIds" : "")
                + " ORDER BY c.name";
        var q = em.createQuery(jpql, Customer.class);
        if (tenantId != null) q.setParameter("tenantId", tenantId);
        return q.getResultList().stream().map(c -> RouteCustomerDto.from(c, null)).toList();
    }

    // ── Route suggestions (V113) ─────────────────────────────────────────────

    /** Customers reps suggested for this route (captured as leads while working it), newest first. */
    @Transactional(readOnly = true)
    public List<RouteSuggestionDto> suggestions(UUID routeId) {
        findRoute(routeId);
        return em.createQuery("SELECT c FROM Customer c LEFT JOIN FETCH c.suggestedBy"
                        + " WHERE c.suggestedRoute.id = :r AND c.deletedAt IS NULL ORDER BY c.suggestedAt DESC", Customer.class)
                .setParameter("r", routeId)
                .getResultList().stream().map(RouteSuggestionDto::from).toList();
    }

    /** Accepts a suggestion: the customer becomes the route's next (last) stop. */
    public RouteDetailDto acceptSuggestion(UUID routeId, UUID customerId, UUID actingUserId) {
        SalesRoute route = findRoute(routeId);
        Customer c = suggestedCustomer(routeId, customerId);
        if (!c.getChannelIds().contains(route.getTenant().getId())) {
            throw new BusinessException("Customer \"" + c.getName() + "\" isn't available in this route's channel.");
        }
        if (route.getCustomers().stream().noneMatch(x -> x.getId().equals(customerId))) {
            route.getCustomers().add(c);
        }
        c.setSuggestedRoute(null);
        c.setSuggestedBy(null);
        c.setSuggestedAt(null);
        SalesRoute saved = routeRepository.save(route);
        auditLogService.log(actingUserId, "ACCEPT_SUGGESTION", "SALES_ROUTE", routeId, null,
                Map.of("customer", c.getName(), "stop", saved.getCustomers().size()));
        return RouteDetailDto.from(saved);
    }

    /** Dismisses a suggestion without adding the customer. */
    public void dismissSuggestion(UUID routeId, UUID customerId, UUID actingUserId) {
        findRoute(routeId);
        Customer c = suggestedCustomer(routeId, customerId);
        c.setSuggestedRoute(null);
        c.setSuggestedBy(null);
        c.setSuggestedAt(null);
        customerRepository.save(c);
        auditLogService.log(actingUserId, "DISMISS_SUGGESTION", "SALES_ROUTE", routeId, null, Map.of("customer", c.getName()));
    }

    private Customer suggestedCustomer(UUID routeId, UUID customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        c = TenantGuard.requireVisible(c, "Customer", customerId);
        if (c.getSuggestedRoute() == null || !c.getSuggestedRoute().getId().equals(routeId)) {
            throw new BusinessException("\"" + c.getName() + "\" isn't suggested for this route (it may already be handled).");
        }
        return c;
    }

    /** Stops now on the route no longer need a suggestion for it. */
    private void clearSuggestionsFulfilledBy(SalesRoute route) {
        for (Customer c : route.getCustomers()) {
            if (c.getSuggestedRoute() != null && c.getSuggestedRoute().getId().equals(route.getId())) {
                c.setSuggestedRoute(null);
                c.setSuggestedBy(null);
                c.setSuggestedAt(null);
            }
        }
    }

    private Map<UUID, Integer> suggestionCounts(List<SalesRoute> routes) {
        if (routes.isEmpty()) return Map.of();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : routeRepository.countSuggestions(routes.stream().map(SalesRoute::getId).toList())) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    // ── Assignments ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssignmentDto> assignments(LocalDate from, LocalDate to, UUID salesRepId, UUID routeId) {
        if (to.isBefore(from)) {
            throw new BusinessException("'to' must not be before 'from'.");
        }
        return assignmentRepository.findInRange(from, to, salesRepId, routeId).stream()
                .map(AssignmentDto::from).toList();
    }

    /** Assigns the route to the rep on each date, replacing that rep's existing route that day. */
    public List<AssignmentDto> assign(AssignRouteRequest req, UUID actingUserId) {
        SalesRoute route = findRoute(req.routeId());
        if (route.getStatus() != SalesRoute.RouteStatus.ACTIVE) {
            throw new BusinessException("Route \"" + route.getName() + "\" is inactive — activate it before assigning.");
        }
        User rep = userRepository.findById(req.salesRepId())
                .orElseThrow(() -> new ResourceNotFoundException("User", req.salesRepId()));
        if (!Role.SALES_REP.equals(rep.getRole().getName())) {
            throw new BusinessException(displayName(rep) + " is not a sales rep.");
        }
        UUID routeTenantId = route.getTenant().getId();
        boolean member = tenantRepository.findByUserId(rep.getId()).stream()
                .anyMatch(t -> t.getId().equals(routeTenantId));
        if (!member) {
            throw new BusinessException(displayName(rep) + " doesn't belong to the route's channel ("
                    + route.getTenant().getName() + ").");
        }

        LocalDate today = LocalDate.now();
        User actor = userRepository.getReferenceById(actingUserId);
        List<AssignmentDto> result = new ArrayList<>();
        for (LocalDate date : new TreeSet<>(req.dates())) {
            if (date.isBefore(today)) {
                throw new BusinessException("Can't assign a route to a past date (" + date + ").");
            }
            // The per-rep-per-day rule spans channels; a same-day route in another channel
            // can't be seen (or replaced) from here.
            UUID otherChannelAssignment = findAssignmentIdOutsideChannel(rep.getId(), date, routeTenantId);
            if (otherChannelAssignment != null) {
                throw new BusinessException(displayName(rep) + " already has a route in another channel on " + date + ".");
            }
            SalesRouteAssignment a = assignmentRepository.findBySalesRepIdAndAssignmentDate(rep.getId(), date)
                    .orElseGet(SalesRouteAssignment::new);
            boolean replacing = a.getId() != null;
            a.setTenant(route.getTenant());
            a.setRoute(route);
            a.setSalesRep(rep);
            a.setAssignmentDate(date);
            a.setNotes(blankToNull(req.notes()));
            a.setAssignedBy(actor);
            SalesRouteAssignment saved = assignmentRepository.save(a);
            auditLogService.log(actingUserId, replacing ? "REASSIGN" : "ASSIGN", "SALES_ROUTE_ASSIGNMENT",
                    saved.getId(), null, Map.of("route", route.getName(), "salesRep", displayName(rep),
                            "date", date.toString()));
            result.add(AssignmentDto.from(saved));
        }
        return result;
    }

    public void unassign(UUID assignmentId, UUID actingUserId) {
        SalesRouteAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Route assignment", assignmentId));
        a = TenantGuard.requireVisible(a, "Route assignment", assignmentId);
        if (a.getAssignmentDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Past assignments can't be removed.");
        }
        assignmentRepository.delete(a);
        auditLogService.log(actingUserId, "UNASSIGN", "SALES_ROUTE_ASSIGNMENT", assignmentId, null,
                Map.of("route", a.getRoute().getName(), "salesRep", displayName(a.getSalesRep()),
                       "date", a.getAssignmentDate().toString()));
    }

    /** The calling rep's route for a day (their active channel). No route at all while the
     *  global sales_routes_enabled setting is off — reps then work their normal customer list. */
    @Transactional(readOnly = true)
    public MyRouteDto myRoute(UUID salesRepId, LocalDate date) {
        if (!systemSettingService.isSalesRoutesEnabled()) {
            return new MyRouteDto(date, null, null, false, List.of());
        }
        return assignmentRepository.findBySalesRepIdAndAssignmentDate(salesRepId, date)
                .map(a -> new MyRouteDto(date, AssignmentDto.from(a), RouteDetailDto.from(a.getRoute()), true,
                        stopStatuses(salesRepId, date, a.getRoute())))
                .orElseGet(() -> new MyRouteDto(date, null, null, true, List.of()));
    }

    /** Which of the route's stops this rep ordered for and/or left a no-order visit note on, that day. */
    private List<VisitNoteDtos.StopStatusDto> stopStatuses(UUID repId, LocalDate date, SalesRoute route) {
        List<UUID> stopIds = route.getCustomers().stream().map(Customer::getId).toList();
        if (stopIds.isEmpty()) return List.of();

        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        List<Object[]> orders = em.createQuery(
                        "SELECT o.customer.id, o.orderNumber FROM Order o"
                                + " WHERE o.salesRep.id = :rep AND o.customer.id IN :ids"
                                + " AND o.orderDate >= :start AND o.orderDate < :end"
                                + " AND o.status <> :cancelled ORDER BY o.orderDate", Object[].class)
                .setParameter("rep", repId)
                .setParameter("ids", stopIds)
                .setParameter("start", date.atStartOfDay(zone).toInstant())
                .setParameter("end", date.plusDays(1).atStartOfDay(zone).toInstant())
                .setParameter("cancelled", Order.OrderStatus.CANCELLED)
                .getResultList();
        Map<UUID, List<String>> orderNumbers = new LinkedHashMap<>();
        for (Object[] row : orders) {
            orderNumbers.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        Map<UUID, VisitNoteDtos.VisitNoteDto> notes = new HashMap<>();
        visitNoteRepository.findForRepOnDate(repId, date)
                .forEach(v -> notes.put(v.getCustomer().getId(), VisitNoteDtos.VisitNoteDto.from(v)));

        List<VisitNoteDtos.StopStatusDto> result = new ArrayList<>();
        for (UUID id : stopIds) {
            List<String> nums = orderNumbers.getOrDefault(id, List.of());
            VisitNoteDtos.VisitNoteDto note = notes.get(id);
            if (!nums.isEmpty() || note != null) {
                result.add(new VisitNoteDtos.StopStatusDto(id, !nums.isEmpty(), nums, note));
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SalesRoute findRoute(UUID id) {
        SalesRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route", id));
        return TenantGuard.requireVisible(route, "Route", id);
    }

    /** Resolves the ordered stop ids (duplicates dropped, first position kept) into customers of the channel. */
    private List<Customer> resolveStops(List<UUID> ids, UUID tenantId) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        List<UUID> ordered = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<UUID, Customer> byId = new HashMap<>();
        customerRepository.findAllById(ordered).forEach(c -> byId.put(c.getId(), c));
        List<Customer> stops = new ArrayList<>();
        for (UUID cid : ordered) {
            Customer c = byId.get(cid);
            if (c == null || c.getDeletedAt() != null) {
                throw new ResourceNotFoundException("Customer", cid);
            }
            if (!c.getChannelIds().contains(tenantId)) {
                throw new BusinessException("Customer \"" + c.getName() + "\" isn't available in this route's channel.");
            }
            stops.add(c);
        }
        return stops;
    }

    private UUID findAssignmentIdOutsideChannel(UUID repId, LocalDate date, UUID tenantId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT id FROM sales_route_assignments WHERE sales_rep_id = :rep AND assignment_date = :d AND tenant_id <> :t")
                .setParameter("rep", repId).setParameter("d", date).setParameter("t", tenantId)
                .getResultList();
        return rows.isEmpty() ? null : (UUID) rows.get(0);
    }

    private Map<UUID, Integer> stopCounts(List<SalesRoute> routes) {
        if (routes.isEmpty()) return Map.of();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : routeRepository.countStops(routes.stream().map(SalesRoute::getId).toList())) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private static SalesRoute.RouteStatus parseStatus(String status, boolean allowNull) {
        if (status == null || status.isBlank()) {
            return allowNull ? null : SalesRoute.RouteStatus.ACTIVE;
        }
        try {
            return SalesRoute.RouteStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown route status: " + status);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String displayName(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }
}
