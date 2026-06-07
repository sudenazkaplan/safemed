package com.safemed.audit_compliance_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// body used to seed/create a log entry
public record AuditLogRequest(

        @NotBlank(message = "trackingId is required")
        @Size(max = 64)
        String trackingId,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "action is required")
        String action,

        @NotBlank(message = "status is required")
        String status,

        String details
) {
}
