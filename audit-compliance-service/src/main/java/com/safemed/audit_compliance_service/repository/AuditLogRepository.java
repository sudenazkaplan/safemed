package com.safemed.audit_compliance_service.repository;

import com.safemed.audit_compliance_service.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // global listing, newest first
    List<AuditLog> findAllByOrderByTimestampDesc();

    // full lifecycle of one record, oldest first
    List<AuditLog> findByTrackingIdOrderByTimestampAsc(String trackingId);

    // anomalies only, newest first
    List<AuditLog> findByStatusOrderByTimestampDesc(String status);

    long countByStatus(String status);
}
