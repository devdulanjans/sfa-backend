package com.sfa.dto.user;

import com.sfa.dto.distributor.DistributorDto;
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
        int     assignedCustomerCount,
        boolean customerAccessAll,
        List<UUID> assignedCustomerIds
) {
    public static UserDto from(User u) {
        List<DistributorDto> dists = u.getDistributors() != null
                ? u.getDistributors().stream().map(DistributorDto::from).toList()
                : List.of();
        List<UUID> customerIds = u.getAssignedCustomers() != null
                ? u.getAssignedCustomers().stream().map(c -> c.getId()).toList()
                : List.of();
        int customerCount = customerIds.size();
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
                customerCount,
                customerCount == 0,
                customerIds
        );
    }
}
