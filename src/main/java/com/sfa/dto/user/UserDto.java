package com.sfa.dto.user;

import com.sfa.dto.distributor.DistributorDto;
import com.sfa.dto.tenant.TenantDto;
import com.sfa.entity.User;

import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID   id,
        String username,
        String email,
        String fullName,
        UUID   roleId,
        String roleName,
        String status,
        UUID   customerId,
        List<DistributorDto> distributors,
        List<TenantDto> tenants,
        UUID   defaultTenantId,
        int     assignedCustomerCount,
        boolean customerAccessAll,
        List<UUID> assignedCustomerIds,
        int     customerGroupCount,
        List<UUID> customerGroupIds,
        boolean mustChangePassword
) {
    public static UserDto from(User u) {
        List<DistributorDto> dists = u.getDistributors() != null
                ? u.getDistributors().stream().map(DistributorDto::from).toList()
                : List.of();
        List<TenantDto> tenants = u.getTenants() != null
                ? u.getTenants().stream().map(TenantDto::from).toList()
                : List.of();
        List<UUID> customerIds = u.getAssignedCustomers() != null
                ? u.getAssignedCustomers().stream().map(c -> c.getId()).toList()
                : List.of();
        List<UUID> groupIds = u.getCustomerGroups() != null
                ? u.getCustomerGroups().stream().map(g -> g.getId()).toList()
                : List.of();
        int customerCount = customerIds.size();
        int groupCount = groupIds.size();
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getRole() != null ? u.getRole().getId() : null,
                u.getRole() != null ? u.getRole().getName() : null,
                u.getStatus() != null ? u.getStatus().name() : null,
                u.getCustomer() != null ? u.getCustomer().getId() : null,
                dists,
                tenants,
                u.getDefaultTenant() != null ? u.getDefaultTenant().getId() : null,
                customerCount,
                customerCount == 0 && groupCount == 0,
                customerIds,
                groupCount,
                groupIds,
                Boolean.TRUE.equals(u.getMustChangePassword())
        );
    }
}
