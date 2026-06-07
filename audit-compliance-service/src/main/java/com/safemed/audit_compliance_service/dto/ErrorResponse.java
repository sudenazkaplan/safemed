package com.safemed.audit_compliance_service.dto;

import java.time.LocalDateTime;

// standard error body returned by the advice
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String path
) {
}
