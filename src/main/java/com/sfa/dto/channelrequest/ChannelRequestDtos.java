package com.sfa.dto.channelrequest;

import com.sfa.entity.ChannelRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTOs for the super admin's review of "Create your SFA" requests. */
public final class ChannelRequestDtos {

    private ChannelRequestDtos() {}

    public record ChannelRequestDto(
            UUID id,
            String referenceNo,
            String status,
            String businessName,
            String businessType,
            String businessRegNo,
            String address,
            String city,
            String ownerName,
            String email,
            String phone,
            String planCode,
            String planName,
            BigDecimal pricePerUser,
            Integer userCount,
            /** userCount × the plan's price per user, at the plan's current price. */
            BigDecimal monthlyAmount,
            String message,
            String reviewNote,
            Instant reviewedAt,
            UUID tenantId,
            String tenantCode,
            UUID ownerUserId,
            Instant createdAt
    ) {
        public static ChannelRequestDto from(ChannelRequest r) {
            BigDecimal price = r.getPlan().getPricePerUser();
            return new ChannelRequestDto(
                    r.getId(), r.getReferenceNo(), r.getStatus().name(),
                    r.getBusinessName(), r.getBusinessType(), r.getBusinessRegNo(), r.getAddress(), r.getCity(),
                    r.getOwnerName(), r.getEmail(), r.getPhone(),
                    r.getPlan().getCode(), r.getPlan().getName(), price, r.getUserCount(),
                    price.multiply(BigDecimal.valueOf(r.getUserCount())),
                    r.getMessage(), r.getReviewNote(), r.getReviewedAt(),
                    r.getTenant() != null ? r.getTenant().getId() : null,
                    r.getTenant() != null ? r.getTenant().getCode() : null,
                    r.getOwnerUserId(), r.getCreatedAt());
        }
    }

    /** All optional — blank fields are generated from the request (business name, owner email). */
    public record ApproveRequest(
            @Size(max = 30) @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9-]*$",
                    message = "Use letters, numbers and dashes only.") String channelCode,
            @Size(max = 150) String channelName,
            @Size(max = 50) @Pattern(regexp = "^$|^[a-z0-9._]{3,50}$",
                    message = "3–50 lowercase letters, numbers, dots or underscores.") String username
    ) {}

    public record RejectRequest(@NotBlank(message = "Give a reason.") @Size(max = 1000) String note) {}

    /** Returned once on approval: the owner's temporary password is never stored in plain text. */
    public record ApprovalResult(
            ChannelRequestDto request,
            String channelCode,
            String ownerUsername,
            String temporaryPassword
    ) {}

    public record Counts(long pending, long approved, long rejected) {}
}
