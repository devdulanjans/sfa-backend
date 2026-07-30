package com.sfa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxPayableSummaryDto(UUID accountId, BigDecimal balance) {}
