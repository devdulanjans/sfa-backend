package com.sfa.dto.tenant;

import java.util.List;
import java.util.UUID;

public record BulkAssignResultDto(
        int totalCount,
        int successCount,
        int errorCount,
        List<RowError> errors
) {
    public record RowError(UUID id, String label, String message) {}
}
