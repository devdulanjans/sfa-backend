package com.sfa.dto.publicsite;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of the public "Create your SFA" form (POST /api/public/channel-requests). */
public record SubmitChannelRequest(
        @NotBlank(message = "Enter your business name.") @Size(max = 150) String businessName,
        @NotBlank(message = "Choose your business type.") @Size(max = 60) String businessType,
        @Size(max = 50) String businessRegNo,
        @Size(max = 255) String address,
        @NotBlank(message = "Enter your city.") @Size(max = 100) String city,
        @NotBlank(message = "Enter the owner's name.") @Size(max = 150) String ownerName,
        @NotBlank(message = "Enter a valid email address.") @Email(message = "Enter a valid email address.")
        @Size(max = 150) String email,
        // Sri Lankan mobile/landline: 0XXXXXXXXX or +94XXXXXXXXX (the form strips spaces/dashes).
        @NotBlank(message = "Enter a mobile number like 077 123 4567.")
        @Pattern(regexp = "^(\\+94|0)\\d{9}$", message = "Enter a mobile number like 077 123 4567.") String phone,
        @NotBlank(message = "Choose a plan.") @Size(max = 30) String planId,
        @NotNull(message = "Enter at least 1 user.") @Min(value = 1, message = "Enter at least 1 user.")
        @Max(value = 10000, message = "Enter 10,000 users or fewer.") Integer userCount,
        @Size(max = 1000) String message
) {}
