package com.sfa.dto.role;

import com.sfa.entity.Role;
import java.util.UUID;

public record RoleDto(UUID id, String name, String description) {
    public static RoleDto from(Role r) {
        return new RoleDto(r.getId(), r.getName(), r.getDescription());
    }
}
