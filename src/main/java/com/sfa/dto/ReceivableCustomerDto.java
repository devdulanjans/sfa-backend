package com.sfa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceivableCustomerDto(
        UUID customerId,
        String customerName,
        BigDecimal outstandingBalance
) {}
