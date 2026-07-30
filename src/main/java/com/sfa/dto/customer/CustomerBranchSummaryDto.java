package com.sfa.dto.customer;

import java.math.BigDecimal;

public record CustomerBranchSummaryDto(int branchCount, BigDecimal totalOutstanding) {}
