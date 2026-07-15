package com.sfa.controller;

import com.sfa.dto.role.RoleDto;
import com.sfa.entity.Role;
import com.sfa.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    // PLATFORM_OWNER is filtered out — it sits above SUPER_ADMIN for this install's own
    // license screen and must never appear as an assignable role in the Users UI.
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<RoleDto> list() {
        return roleRepository.findAll().stream()
                .filter(r -> !Role.PLATFORM_OWNER.equals(r.getName()))
                .map(RoleDto::from)
                .toList();
    }
}
