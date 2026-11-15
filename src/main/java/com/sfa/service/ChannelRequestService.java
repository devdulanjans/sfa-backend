package com.sfa.service;

import com.sfa.dto.channelrequest.ChannelRequestDtos.ApprovalResult;
import com.sfa.dto.channelrequest.ChannelRequestDtos.ApproveRequest;
import com.sfa.dto.channelrequest.ChannelRequestDtos.ChannelRequestDto;
import com.sfa.dto.channelrequest.ChannelRequestDtos.Counts;
import com.sfa.dto.publicsite.PublicPlanDto;
import com.sfa.dto.publicsite.SubmitChannelRequest;
import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.entity.ChannelRequest;
import com.sfa.entity.CompanyProfile;
import com.sfa.entity.Permission;
import com.sfa.entity.Role;
import com.sfa.entity.SubscriptionPlan;
import com.sfa.entity.SystemModule;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ChannelRequestRepository;
import com.sfa.repository.CompanyProfileRepository;
import com.sfa.repository.PermissionRepository;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.SubscriptionPlanRepository;
import com.sfa.repository.SystemModuleRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The public "Create your SFA" flow: a business submits a request on the website, the super
 * admin reviews it, and approval creates the business's channel, its Company Profile and an
 * owner login (role ADMIN — the channel-scoped administrator) with every module granted.
 * Payment for paid plans is collected manually, outside the system.
 */
@Service
@RequiredArgsConstructor
public class ChannelRequestService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChannelRequestRepository requestRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserPermissionService userPermissionService;
    private final SystemModuleRepository systemModuleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLog;
    private final MembershipService membershipService;

    // ── Public ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PublicPlanDto> activePlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(PublicPlanDto::from).toList();
    }

    @Transactional
    public String submit(SubmitChannelRequest req, String sourceIp) {
        SubscriptionPlan plan = planRepository.findByCode(req.planId().trim())
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new BusinessException("That plan is no longer available. Please choose another."));
        if (plan.getMaxUsers() != null && req.userCount() > plan.getMaxUsers()) {
            throw new BusinessException("The " + plan.getName() + " plan allows up to " + plan.getMaxUsers()
                    + " user" + (plan.getMaxUsers() == 1 ? "" : "s") + ". Choose a bigger plan or fewer users.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        requestRepository.findPendingByEmail(email).ifPresent(existing -> {
            throw new BusinessException("We already have a request from this email (reference "
                    + existing.getReferenceNo() + ") and are reviewing it. We'll contact you soon.");
        });

        String referenceNo = "REQ-" + Year.now().getValue() + "-"
                + String.format("%05d", requestRepository.nextReferenceNumber());
        ChannelRequest saved = requestRepository.save(ChannelRequest.builder()
                .referenceNo(referenceNo)
                .businessName(req.businessName().trim())
                .businessType(req.businessType().trim())
                .businessRegNo(blankToNull(req.businessRegNo()))
                .address(blankToNull(req.address()))
                .city(req.city().trim())
                .ownerName(req.ownerName().trim())
                .email(email)
                .phone(req.phone().trim())
                .plan(plan)
                .userCount(req.userCount())
                .message(blankToNull(req.message()))
                .sourceIp(sourceIp)
                .build());
        auditLog.log(null, "SUBMIT", "CHANNEL_REQUEST", saved.getId(), null,
                Map.of("referenceNo", referenceNo, "businessName", saved.getBusinessName(), "plan", plan.getCode()));
        return referenceNo;
    }

    // ── Super admin review ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ChannelRequestDto> list(ChannelRequest.Status status, Pageable pageable) {
        Page<ChannelRequest> page = status != null
                ? requestRepository.findByStatus(status, pageable)
                : requestRepository.findAll(pageable);
        return page.map(ChannelRequestDto::from);
    }

    @Transactional(readOnly = true)
    public Counts counts() {
        return new Counts(requestRepository.countByStatus(ChannelRequest.Status.PENDING),
                requestRepository.countByStatus(ChannelRequest.Status.APPROVED),
                requestRepository.countByStatus(ChannelRequest.Status.REJECTED));
    }

    @Transactional(readOnly = true)
    public ChannelRequestDto get(UUID id) {
        return ChannelRequestDto.from(findOrThrow(id));
    }

    /**
     * Creates the channel, its Company Profile and the owner login in one transaction — any
     * failure (taken channel code, email already registered…) leaves nothing half-created.
     */
    @Transactional
    public ApprovalResult approve(UUID id, ApproveRequest body, UUID actorId) {
        ChannelRequest r = findPendingOrThrow(id);
        // One person, many roles (V119): if the requester already has an account (e.g. signed up
        // on the website, or works for another business), that account becomes the owner.
        com.sfa.entity.User existingAccount = userRepository.findByEmailIgnoreCase(r.getEmail()).orElse(null);

        String code = hasText(body != null ? body.channelCode() : null)
                ? body.channelCode().trim().toUpperCase(Locale.ROOT)
                : uniqueChannelCode(r.getBusinessName());
        if (tenantRepository.existsByCode(code)) {
            throw new BusinessException("Channel code " + code + " is already used. Choose another.");
        }
        String channelName = hasText(body != null ? body.channelName() : null)
                ? body.channelName().trim() : r.getBusinessName();
        Tenant tenant = tenantRepository.save(Tenant.builder().code(code).name(channelName).build());

        companyProfileRepository.save(CompanyProfile.builder()
                .tenant(tenant)
                .companyName(r.getBusinessName())
                .registeredAddress(joinAddress(r.getAddress(), r.getCity()))
                .operatingAddress(joinAddress(r.getAddress(), r.getCity()))
                .phone(r.getPhone())
                .email(r.getEmail())
                .registrationNumber(r.getBusinessRegNo())
                .build());

        List<String> allKeys = Stream.concat(
                systemModuleRepository.findAll().stream().map(SystemModule::getCode),
                permissionRepository.findAll().stream().map(Permission::getKey)).distinct().toList();

        if (existingAccount != null) {
            com.sfa.entity.Membership owner = membershipService.grantActive(existingAccount, tenant, Role.ADMIN);
            userPermissionService.setMembershipPermissions(owner.getId(), allKeys, actorId);
            return finishApproval(r, tenant, existingAccount.getId(), existingAccount.getUsername(), null, actorId);
        }

        String username = hasText(body != null ? body.username() : null)
                ? body.username().trim() : uniqueUsername(r.getEmail());
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username " + username + " is already taken. Choose another.");
        }
        Role adminRole = roleRepository.findByName(Role.ADMIN)
                .orElseThrow(() -> new BusinessException("The ADMIN role is missing — run the latest migrations."));
        String password = temporaryPassword();
        UserDto owner = userService.create(new CreateUserRequest(
                username, r.getEmail(), r.getOwnerName(), password, adminRole.getId(),
                null, null, null, List.of(tenant.getId()), null, null), actorId);

        // The owner must pick their own password on first sign-in.
        userRepository.findById(owner.id()).ifPresent(u -> {
            u.setMustChangePassword(true);
            userRepository.save(u);
        });
        // Channel owners see every feature (narrowing per plan/channel comes from the super admin).
        userPermissionService.setPermissions(owner.id(), allKeys, actorId);
        return finishApproval(r, tenant, owner.id(), username, password, actorId);
    }

    /** @param temporaryPassword null when an existing account became the owner (it keeps its password) */
    private ApprovalResult finishApproval(ChannelRequest r, Tenant tenant, UUID ownerId, String username,
                                          String temporaryPassword, UUID actorId) {
        r.setStatus(ChannelRequest.Status.APPROVED);
        r.setTenant(tenant);
        r.setOwnerUserId(ownerId);
        r.setReviewedBy(actorId);
        r.setReviewedAt(Instant.now());
        ChannelRequest saved = requestRepository.save(r);
        auditLog.log(actorId, "APPROVE", "CHANNEL_REQUEST", saved.getId(), ChannelRequest.Status.PENDING,
                Map.of("tenantCode", tenant.getCode(), "ownerUsername", username,
                        "existingAccount", temporaryPassword == null));
        return new ApprovalResult(ChannelRequestDto.from(saved), tenant.getCode(), username, temporaryPassword);
    }

    @Transactional
    public ChannelRequestDto reject(UUID id, String note, UUID actorId) {
        ChannelRequest r = findPendingOrThrow(id);
        r.setStatus(ChannelRequest.Status.REJECTED);
        r.setReviewNote(note.trim());
        r.setReviewedBy(actorId);
        r.setReviewedAt(Instant.now());
        ChannelRequest saved = requestRepository.save(r);
        auditLog.log(actorId, "REJECT", "CHANNEL_REQUEST", saved.getId(), ChannelRequest.Status.PENDING,
                Map.of("note", saved.getReviewNote()));
        return ChannelRequestDto.from(saved);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private ChannelRequest findOrThrow(UUID id) {
        return requestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ChannelRequest", id));
    }

    private ChannelRequest findPendingOrThrow(UUID id) {
        ChannelRequest r = findOrThrow(id);
        if (r.getStatus() != ChannelRequest.Status.PENDING) {
            throw new BusinessException("This request was already " + r.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        return r;
    }

    /** "Laksiri Stores (Pvt) Ltd" → "LAKSIRI-STORES-PVT-LTD", suffixed -2, -3… if taken. */
    private String uniqueChannelCode(String businessName) {
        String base = businessName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "CHANNEL";
        if (base.length() > 26) base = base.substring(0, 26).replaceAll("-+$", "");
        String code = base;
        for (int n = 2; tenantRepository.existsByCode(code); n++) code = base + "-" + n;
        return code;
    }

    /** "laksiri.mahanama@gmail.com" → "laksiri.mahanama", suffixed 2, 3… if taken. */
    private String uniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "");
        if (base.length() < 3) base = (base + "owner").substring(0, Math.max(3, base.length()));
        if (base.length() > 45) base = base.substring(0, 45);
        String name = base;
        for (int n = 2; userRepository.existsByUsername(name); n++) name = base + n;
        return name;
    }

    /** 12 characters with upper, lower, digit and symbol; no look-alike characters. */
    static String temporaryPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnpqrstuvwxyz", digits = "23456789", symbols = "@#$%&*?";
        String all = upper + lower + digits + symbols;
        List<Character> chars = new ArrayList<>();
        chars.add(upper.charAt(RANDOM.nextInt(upper.length())));
        chars.add(lower.charAt(RANDOM.nextInt(lower.length())));
        chars.add(digits.charAt(RANDOM.nextInt(digits.length())));
        chars.add(symbols.charAt(RANDOM.nextInt(symbols.length())));
        while (chars.size() < 12) chars.add(all.charAt(RANDOM.nextInt(all.length())));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    /** "243 Mahara Road" + "Ragama" → "243 Mahara Road, Ragama" — unless the address already names the city. */
    private static String joinAddress(String address, String city) {
        if (!hasText(address)) return hasText(city) ? city.trim() : null;
        if (!hasText(city) || address.toLowerCase(Locale.ROOT).contains(city.trim().toLowerCase(Locale.ROOT))) {
            return address.trim();
        }
        return address.trim() + ", " + city.trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
