package com.sfa.dto;

import java.math.BigDecimal;

/**
 * The signed-in user's current mileage status — startMileage non-null means an
 * open session exists (record end mileage before logout); otherwise
 * lastEndMileage (if any) is shown on the start-mileage screen as a reference
 * for what the odometer read at the end of the last session.
 */
public record CurrentMileageDto(
        BigDecimal startMileage,
        BigDecimal lastEndMileage
) {}
