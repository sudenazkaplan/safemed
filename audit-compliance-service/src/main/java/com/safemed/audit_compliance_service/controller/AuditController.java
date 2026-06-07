package com.safemed.audit_compliance_service.controller;

import com.safemed.audit_compliance_service.dto.AuditLogRequest;
import com.safemed.audit_compliance_service.dto.ComplianceReportResponse;
import com.safemed.audit_compliance_service.model.AuditLog;
import com.safemed.audit_compliance_service.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    // save a new audit log
    @PostMapping("/logs")
    public ResponseEntity<AuditLog> createLog(@Valid @RequestBody AuditLogRequest request) {
        AuditLog saved = auditService.record(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // all logs, newest first
    @GetMapping("/logs")
    public ResponseEntity<List<AuditLog>> getAllLogs() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }

    // anomalies only, separate path avoids {trackingId} clash
    @GetMapping("/logs/filter/anomalies")
    public ResponseEntity<List<AuditLog>> getAnomalies() {
        return ResponseEntity.ok(auditService.getAnomalies());
    }

    // full lifecycle of a single record
    @GetMapping("/logs/{trackingId}")
    public ResponseEntity<List<AuditLog>> getProvenance(@PathVariable String trackingId) {
        return ResponseEntity.ok(auditService.getProvenance(trackingId));
    }

    // quick KVKK/GDPR summary
    @GetMapping("/compliance-report")
    public ResponseEntity<ComplianceReportResponse> complianceReport() {
        return ResponseEntity.ok(auditService.buildComplianceReport());
    }
}
