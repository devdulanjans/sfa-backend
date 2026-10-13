package com.sfa.service;

import com.sfa.dto.route.VisitNoteDtos.*;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.*;
import com.sfa.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * No-order visit notes. A sales rep records one per customer per day (recording again that day
 * updates it); the note is tagged with the rep's route for the day when the customer is on it,
 * and with the rep's distributor, for the admin Visit Notes report.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VisitNoteService {

    private final VisitNoteRepository visitNoteRepository;
    private final CustomerRepository customerRepository;
    private final ReasonRepository reasonRepository;
    private final UserRepository userRepository;
    private final DistributorRepository distributorRepository;
    private final SalesRouteAssignmentRepository assignmentRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public VisitNoteDto record(RecordVisitNoteRequest req, UUID repId) {
        User rep = userRepository.findById(repId)
                .orElseThrow(() -> new ResourceNotFoundException("User", repId));
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
        customer = TenantGuard.requireVisible(customer, "Customer", req.customerId());

        Reason reason = reasonRepository.findById(req.reasonId())
                .orElseThrow(() -> new ResourceNotFoundException("Reason", req.reasonId()));
        if (reason.getType() != Reason.ReasonType.VISIT) {
            throw new BusinessException("That isn't a visit reason.");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (reason.isAllowFreeText() && note == null) {
            throw new BusinessException("Please add a note for \"" + reason.getLabel() + "\".");
        }

        // An offline note keeps the time it was taken — but never a time in the future.
        Instant now = Instant.now();
        Instant visitedAt = req.visitedAt() != null && !req.visitedAt().isAfter(now.plus(5, ChronoUnit.MINUTES))
                ? req.visitedAt() : now;
        LocalDate visitDate = visitedAt.atZone(ZoneId.systemDefault()).toLocalDate();

        VisitNote v = visitNoteRepository
                .findBySalesRepIdAndCustomerIdAndVisitDate(repId, customer.getId(), visitDate)
                .orElseGet(VisitNote::new);
        // The channel the rep is working in, when the customer is available there; else its home.
        UUID active = com.sfa.security.TenantContext.getTenantId();
        v.setTenant(active != null && customer.getChannelIds().contains(active)
                ? em.getReference(Tenant.class, active) : customer.getTenant());
        v.setCustomer(customer);
        v.setSalesRep(rep);
        v.setVisitDate(visitDate);
        v.setVisitedAt(visitedAt);
        v.setReason(reason);
        v.setReasonLabel(reason.getLabel());
        v.setNote(note);
        v.setLatitude(req.latitude());
        v.setLongitude(req.longitude());

        // Tag the rep's route that day, if this customer is one of its stops.
        final UUID customerId = customer.getId();
        assignmentRepository.findBySalesRepIdAndAssignmentDate(repId, visitDate)
                .filter(a -> a.getRoute().getCustomers().stream().anyMatch(c -> c.getId().equals(customerId)))
                .ifPresentOrElse(a -> { v.setAssignment(a); v.setRoute(a.getRoute()); },
                                 () -> { v.setAssignment(null); v.setRoute(null); });

        // Only a distributor the rep actually works under.
        v.setDistributor(req.distributorId() == null ? null
                : distributorRepository.findByUserId(repId).stream()
                        .filter(d -> d.getId().equals(req.distributorId()))
                        .findFirst().orElse(null));

        return VisitNoteDto.from(visitNoteRepository.save(v));
    }

    @Transactional(readOnly = true)
    public List<VisitNoteDto> myNotes(UUID repId, LocalDate date) {
        return visitNoteRepository.findForRepOnDate(repId, date).stream().map(VisitNoteDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<VisitNoteDto> search(LocalDate from, LocalDate to, UUID routeId, UUID salesRepId, UUID tenantId,
                                     UUID distributorId, UUID reasonId, UUID customerId, int page, int size) {
        requireRange(from, to);
        return visitNoteRepository.search(from, to, routeId, salesRepId, tenantId, distributorId, reasonId,
                        customerId, PageRequest.of(page, size))
                .map(VisitNoteDto::from);
    }

    @Transactional(readOnly = true)
    public List<ReasonCountDto> summary(LocalDate from, LocalDate to, UUID routeId, UUID salesRepId, UUID tenantId,
                                        UUID distributorId, UUID customerId) {
        requireRange(from, to);
        return visitNoteRepository.countByReason(from, to, routeId, salesRepId, tenantId, distributorId, customerId)
                .stream().map(r -> new ReasonCountDto((String) r[0], ((Number) r[1]).longValue())).toList();
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new BusinessException("'to' must not be before 'from'.");
    }
}
