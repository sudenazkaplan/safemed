package com.safemed.audit_compliance_service.dto;

import java.time.LocalDateTime;

// crisp KVKK/GDPR summary returned by the report endpoint
public record ComplianceReportResponse(
        long totalOperationsLogged,
        long successfulOperations,
        long unauthorizedAnomalyCount,
        String complianceScore,
        LocalDateTime generatedAt
) {
}
