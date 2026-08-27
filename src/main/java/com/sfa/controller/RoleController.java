package com.sfa.controller;

import com.sfa.dto.role.RoleDto;
import com.sfa.entity.Role;
import com.sfa.repository.RoleRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    // PLATFORM_OWNER is filtered out for everyone — it sits above SUPER_ADMIN for this
    // install's own license screen and must never appear as an assignable role. ADMIN
    // additionally never sees SUPER_ADMIN or other ADMIN roles as options — it can't
    // assign them anyway (UserService.rejectElevatedRoleForAdminActor), so hiding them
    // here keeps its Users form's role picker honest about what it can actually do.
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<RoleDto> list(@AuthenticationPrincipal UserDetailsImpl principal) {
        boolean isSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.SUPER_ADMIN));
        return roleRepository.findAll().stream()
                .filter(r -> !Role.PLATFORM_OWNER.equals(r.getName()))
                .filter(r -> isSuperAdmin || !Set.of(Role.SUPER_ADMIN, Role.ADMIN).contains(r.getName()))
                .map(RoleDto::from)
                .toList();
    }
}
