package com.safemed.audit_compliance_service.service;

import com.safemed.audit_compliance_service.dto.AuditLogRequest;
import com.safemed.audit_compliance_service.dto.ComplianceReportResponse;
import com.safemed.audit_compliance_service.model.AuditLog;

import java.util.List;

public interface AuditService {

    AuditLog record(AuditLogRequest request);

    List<AuditLog> getAllLogs();

    List<AuditLog> getProvenance(String trackingId);

    List<AuditLog> getAnomalies();

    ComplianceReportResponse buildComplianceReport();
}
