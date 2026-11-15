package com.sfa.controller;

import com.sfa.dto.membership.MembershipDto;
import com.sfa.entity.Role;
import com.sfa.exception.BusinessException;
import com.sfa.security.TenantContext;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * One account, many roles (V119). {@code /api/me/...}: a person's own workspaces and the
 * invitations waiting for them. {@code /api/memberships}: a business owner managing who holds
 * which role in their business — adding an existing account is always an invitation.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Memberships")
public class MembershipController {

    private final MembershipService membershipService;

    // ── The signed-in person ────────────────────────────────────────────────────

    @GetMapping("/api/me/memberships")
    @PreAuthorize("isAuthenticated()")
    public List<MembershipDto> myWorkspaces(@AuthenticationPrincipal UserDetailsImpl me) {
        return membershipService.workspacesFor(me.getId());
    }

    @GetMapping("/api/me/invitations")
    @PreAuthorize("isAuthenticated()")
    public List<MembershipDto> myInvitations(@AuthenticationPrincipal UserDetailsImpl me) {
        return membershipService.invitationsFor(me.getId());
    }

    @PostMapping("/api/me/invitations/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public MembershipDto accept(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl me) {
        return membershipService.respond(me.getId(), id, true);
    }

    @PostMapping("/api/me/invitations/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public MembershipDto decline(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl me) {
        return membershipService.respond(me.getId(), id, false);
    }

    // ── A business managing its people ──────────────────────────────────────────

    public record InviteRequest(
            @NotBlank(message = "Enter the person's email or phone number.") String emailOrPhone,
            @NotBlank(message = "Choose a role.") String role,
            /** CUSTOMER role: which of this business's customers the person orders for. */
            UUID customerId) {}

    /** Everyone with a role (or a pending invitation) in the business the caller is working in. */
    @GetMapping("/api/memberships")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<MembershipDto> members() {
        return membershipService.membersOf(requireChannel());
    }

    @PostMapping("/api/memberships/invite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Invite an existing account (by email or phone) to a role in this business")
    public ResponseEntity<MembershipDto> invite(@Valid @RequestBody InviteRequest req,
                                                @AuthenticationPrincipal UserDetailsImpl me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.invite(
                me.getId(), isSuperAdmin(me), requireChannel(), req.emailOrPhone(), req.role(), req.customerId()));
    }

    @DeleteMapping("/api/memberships/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl me) {
        membershipService.revoke(me.getId(), isSuperAdmin(me), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID requireChannel() {
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Enter a channel first (Channels → View as this channel).");
        }
        return tenantId;
    }

    private static boolean isSuperAdmin(UserDetailsImpl me) {
        return Role.SUPER_ADMIN.equals(me.getRoleName());
    }
}
