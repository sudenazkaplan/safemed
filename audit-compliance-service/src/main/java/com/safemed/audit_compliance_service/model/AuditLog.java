package com.safemed.audit_compliance_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// audit trail row, no PII stored here
// own table so we don't collide with ingestion-service's audit_logs in the shared db
@Entity
@Table(name = "audit_trail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_id")
    private String trackingId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "action")
    private String action;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "status")
    private String status;

    // free text, can be long
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}
