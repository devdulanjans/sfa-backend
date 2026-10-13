package com.sfa.service;

import com.sfa.dto.customer.AddressRequest;
import com.sfa.dto.customer.CreateCustomerRequest;
import com.sfa.dto.customer.CustomerDto;
import com.sfa.dto.lead.ConvertLeadRequest;
import com.sfa.dto.lead.CreateLeadRequest;
import com.sfa.dto.lead.LeadDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Lead;
import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.LeadRepository;
import com.sfa.repository.SalesRouteAssignmentRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantGuard;
import com.sfa.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {

    public static final String PERM_CONVERT = "ACTION_CONVERT_LEAD";

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final SystemSettingService settingService;
    private final UserPermissionService permissionService;
    private final TenantAccessService tenantAccessService;
    private final AuditLogService auditLogService;
    private final SalesRouteAssignmentRepository assignmentRepository;

    @PersistenceContext
    private EntityManager em;

    /** SUPER_ADMIN always may; everyone else needs the ACTION_CONVERT_LEAD grant. */
    public boolean canReview(UserDetailsImpl user) {
        if (user == null) return false;
        if (Role.SUPER_ADMIN.equals(user.getRoleName()) || Role.PLATFORM_OWNER.equals(user.getRoleName())) {
            return true;
        }
        return permissionService.hasPermission(user.getId(), PERM_CONVERT);
    }

    /** Reviewers see every lead in the channel; anyone else only the leads they created. */
    @Transactional(readOnly = true)
    public Page<LeadDto> list(UserDetailsImpl user, String search, String status, Pageable pageable) {
        Lead.LeadStatus st = parseStatus(status);
        UUID createdBy = canReview(user) ? null : user.getId();
        return leadRepository.search(search == null ? "" : search.trim(), st, createdBy, pageable)
                .map(LeadDto::from);
    }

    @Transactional(readOnly = true)
    public LeadDto getById(UUID id, UserDetailsImpl user) {
        Lead lead = findOrThrow(id);
        requireOwnerOrReviewer(lead, user);
        return LeadDto.from(lead);
    }

    /**
     * Creates the lead and — when {@code lead_auto_convert_to_customer} is on — converts it into a
     * customer in the same transaction, so the caller's response already says CONVERTED.
     */
    @Transactional
    public LeadDto create(CreateLeadRequest req, UserDetailsImpl user) {
        Lead lead = new Lead();
        applyFields(lead, req);
        lead.setStatus(Lead.LeadStatus.PENDING);
        lead.setLeadNumber(nextLeadNumber());
        lead.setCreatedBy(userRepository.getReferenceById(user.getId()));
        tenantAccessService.resolveExplicitTenant(req.tenantId(), user.getId(), "lead")
                .ifPresent(lead::setTenant);
        suggestRouteOfTheDay(lead, user, req.capturedAt());
        Lead saved = leadRepository.save(lead);
        auditLogService.log(user.getId(), "CREATE", "Lead", saved.getId(), null, LeadDto.from(saved));

        if (settingService.isLeadAutoConvertEnabled()) {
            convertInternal(saved, ConvertLeadRequest.empty(), user.getId());
        }
        return LeadDto.from(saved);
    }

    @Transactional
    public LeadDto update(UUID id, CreateLeadRequest req, UserDetailsImpl user) {
        Lead lead = findOrThrow(id);
        requireOwnerOrReviewer(lead, user);
        if (lead.getStatus() == Lead.LeadStatus.CONVERTED) {
            throw new BusinessException("A converted lead can no longer be edited — edit the customer instead");
        }
        Object before = LeadDto.from(lead);
        applyFields(lead, req);
        // Editing a rejected lead re-submits it for review.
        if (lead.getStatus() == Lead.LeadStatus.REJECTED) {
            lead.setStatus(Lead.LeadStatus.PENDING);
            lead.setRejectionReason(null);
            lead.setReviewedBy(null);
            lead.setReviewedAt(null);
        }
        Lead saved = leadRepository.save(lead);
        auditLogService.log(user.getId(), "UPDATE", "Lead", saved.getId(), before, LeadDto.from(saved));
        return LeadDto.from(saved);
    }

    @Transactional
    public void delete(UUID id, UserDetailsImpl user) {
        Lead lead = findOrThrow(id);
        requireOwnerOrReviewer(lead, user);
        if (lead.getStatus() == Lead.LeadStatus.CONVERTED) {
            throw new BusinessException("A converted lead cannot be deleted");
        }
        leadRepository.delete(lead);
        auditLogService.log(user.getId(), "DELETE", "Lead", id, null, null);
    }

    @Transactional
    public LeadDto convert(UUID id, ConvertLeadRequest req, UserDetailsImpl user) {
        requireReviewer(user);
        Lead lead = findOrThrow(id);
        if (lead.getStatus() == Lead.LeadStatus.CONVERTED) {
            throw new BusinessException("Lead " + lead.getLeadNumber() + " is already converted");
        }
        convertInternal(lead, req != null ? req : ConvertLeadRequest.empty(), user.getId());
        return LeadDto.from(lead);
    }

    @Transactional
    public LeadDto reject(UUID id, String reason, UserDetailsImpl user) {
        requireReviewer(user);
        Lead lead = findOrThrow(id);
        if (lead.getStatus() != Lead.LeadStatus.PENDING) {
            throw new BusinessException("Only PENDING leads can be rejected");
        }
        lead.setStatus(Lead.LeadStatus.REJECTED);
        lead.setRejectionReason(reason == null || reason.isBlank() ? null : reason.trim());
        lead.setReviewedBy(userRepository.getReferenceById(user.getId()));
        lead.setReviewedAt(Instant.now());
        Lead saved = leadRepository.save(lead);
        auditLogService.log(user.getId(), "REJECT", "Lead", id, Lead.LeadStatus.PENDING, Lead.LeadStatus.REJECTED);
        return LeadDto.from(saved);
    }

    /**
     * A sales rep capturing a lead while working an assigned route that day (in the lead's own
     * channel) tags the lead with that route, so admins can add the resulting customer to it.
     */
    private void suggestRouteOfTheDay(Lead lead, UserDetailsImpl user, Instant capturedAt) {
        if (!Role.SALES_REP.equals(user.getRoleName())) return;
        Instant now = Instant.now();
        Instant at = capturedAt != null && !capturedAt.isAfter(now.plusSeconds(300)) ? capturedAt : now;
        java.time.LocalDate day = at.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        assignmentRepository.findBySalesRepIdAndAssignmentDate(user.getId(), day)
                .filter(a -> lead.getTenant() == null
                        || a.getRoute().getTenant().getId().equals(lead.getTenant().getId()))
                .ifPresent(a -> {
                    lead.setSuggestedRoute(a.getRoute());
                    lead.setSuggestedBy(userRepository.getReferenceById(user.getId()));
                    lead.setSuggestedAt(at);
                });
    }

    private void convertInternal(Lead lead, ConvertLeadRequest req, UUID actingUserId) {
        String code = req.customerCode() == null || req.customerCode().isBlank()
                ? nextCustomerCode()
                : req.customerCode().trim();
        String addressLine = firstNonBlank(lead.getAddressLine(), lead.getLocation(), lead.getName());

        CreateCustomerRequest customerReq = new CreateCustomerRequest(
                code, lead.getName(), lead.getContactPerson(), lead.getPhone(), lead.getEmail(), null,
                lead.getLocation(), lead.getPlaceOfSupplier(), null,
                req.taxType(), null, req.categoryId(), null,
                req.creditLimit(), req.creditDays(), null, false,
                List.of(new AddressRequest("Main", addressLine)),
                null,
                // Pin the customer to the lead's own channel (matters for an unscoped SUPER_ADMIN).
                lead.getTenant() != null ? lead.getTenant().getId() : null,
                lead.getLatitude(), lead.getLongitude(),
                null);
        CustomerDto created = customerService.create(customerReq, actingUserId);
        Customer customer = customerRepository.findById(created.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", created.id()));

        // The route the rep was working when they captured the lead becomes the customer's suggestion.
        if (lead.getSuggestedRoute() != null) {
            customer.setSuggestedRoute(lead.getSuggestedRoute());
            customer.setSuggestedBy(lead.getSuggestedBy());
            customer.setSuggestedAt(lead.getSuggestedAt());
            customerRepository.save(customer);
        }

        lead.setStatus(Lead.LeadStatus.CONVERTED);
        lead.setConvertedCustomer(customer);
        lead.setReviewedBy(userRepository.getReferenceById(actingUserId));
        lead.setReviewedAt(Instant.now());
        lead.setRejectionReason(null);
        leadRepository.save(lead);

        assignToCreatorIfRestricted(lead, customer);
        auditLogService.log(actingUserId, "CONVERT", "Lead", lead.getId(), null, created.id());
    }

    /**
     * A sales rep with a non-empty customer scope (direct assignments or customer groups) would
     * otherwise never see the customer their own lead turned into — an empty scope already means
     * "all customers", so it's left untouched (adding one would restrict the rep to just it).
     */
    private void assignToCreatorIfRestricted(Lead lead, Customer customer) {
        if (lead.getCreatedBy() == null) return;
        User rep = userRepository.findById(lead.getCreatedBy().getId()).orElse(null);
        if (rep == null || !Role.SALES_REP.equals(rep.getRole().getName())) return;
        if (rep.getAssignedCustomers().isEmpty() && rep.getCustomerGroups().isEmpty()) return;
        rep.getAssignedCustomers().add(customer);
        userRepository.save(rep);
    }

    private void applyFields(Lead lead, CreateLeadRequest req) {
        lead.setName(req.name().trim());
        lead.setContactPerson(blankToNull(req.contactPerson()));
        lead.setPhone(blankToNull(req.phone()));
        lead.setEmail(blankToNull(req.email()));
        lead.setLocation(blankToNull(req.location()));
        lead.setPlaceOfSupplier(blankToNull(req.placeOfSupplier()));
        lead.setAddressLine(blankToNull(req.addressLine()));
        lead.setLatitude(req.latitude());
        lead.setLongitude(req.longitude());
        lead.setNotes(blankToNull(req.notes()));
    }

    private Lead findOrThrow(UUID id) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead", id));
        return TenantGuard.requireVisible(lead, "Lead", id);
    }

    private void requireReviewer(UserDetailsImpl user) {
        if (!canReview(user)) {
            throw new AccessDeniedException("You don't have permission to convert or reject leads");
        }
    }

    private void requireOwnerOrReviewer(Lead lead, UserDetailsImpl user) {
        boolean owner = lead.getCreatedBy() != null && lead.getCreatedBy().getId().equals(user.getId());
        if (!owner && !canReview(user)) {
            throw new AccessDeniedException("Lead not accessible");
        }
    }

    private Lead.LeadStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Lead.LeadStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid lead status: " + status);
        }
    }

    private String nextLeadNumber() {
        Number seq = (Number) em.createNativeQuery("SELECT NEXTVAL('lead_number_seq')").getSingleResult();
        return "LEAD-" + String.format("%05d", seq.longValue());
    }

    /** Same sequence/format as CustomerImportService; skips any code already typed in by hand. */
    private String nextCustomerCode() {
        while (true) {
            Number seq = (Number) em.createNativeQuery("SELECT NEXTVAL('customer_code_seq')").getSingleResult();
            String code = "CUST-" + String.format("%05d", seq.longValue());
            if (customerRepository.findByCustomerCode(code).isEmpty()) return code;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "-";
    }
}
