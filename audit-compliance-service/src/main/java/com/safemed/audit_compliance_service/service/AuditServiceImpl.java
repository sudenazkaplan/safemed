package com.safemed.audit_compliance_service.service;

import com.safemed.audit_compliance_service.dto.AuditLogRequest;
import com.safemed.audit_compliance_service.dto.ComplianceReportResponse;
import com.safemed.audit_compliance_service.exception.ResourceNotFoundException;
import com.safemed.audit_compliance_service.model.AuditLog;
import com.safemed.audit_compliance_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ANOMALY = "ANOMALY";

    private final AuditLogRepository repository;

    @Override
    public AuditLog record(AuditLogRequest request) {
        AuditLog entry = AuditLog.builder()
                .trackingId(request.trackingId())
                .userId(request.userId())
                .action(request.action())
                .status(request.status())
                .details(request.details())
                .timestamp(LocalDateTime.now())
                .build();
        AuditLog saved = repository.save(entry);
        log.info("Audit log stored id={} trackingId={} action={}",
                saved.getId(), saved.getTrackingId(), saved.getAction());
        return saved;
    }

    @Override
    public List<AuditLog> getAllLogs() {
        return repository.findAllByOrderByTimestampDesc();
    }

    @Override
    public List<AuditLog> getProvenance(String trackingId) {
        List<AuditLog> history = repository.findByTrackingIdOrderByTimestampAsc(trackingId);
        if (history.isEmpty()) {
            throw new ResourceNotFoundException("No audit trail found for trackingId: " + trackingId);
        }
        return history;
    }

    @Override
    public List<AuditLog> getAnomalies() {
        return repository.findByStatusOrderByTimestampDesc(STATUS_ANOMALY);
    }

    @Override
    public ComplianceReportResponse buildComplianceReport() {
        long total = repository.count();
        long success = repository.countByStatus(STATUS_SUCCESS);
        long anomalies = repository.countByStatus(STATUS_ANOMALY);

        // compliance = share of non-anomaly operations
        double percentage = total == 0 ? 100.0 : (double) (total - anomalies) * 100.0 / total;
        // Locale.US so the decimal stays a dot, not a comma
        String score = String.format(Locale.US, "%.2f%%", percentage);

        return new ComplianceReportResponse(total, success, anomalies, score, LocalDateTime.now());
    }
}
