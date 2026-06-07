package com.safemed.schema_registry.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "hospital_schemas")
@Data
public class HospitalSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "format_type", nullable = false)
    private String formatType;

    @Column(name = "mapping_rules", columnDefinition = "TEXT")
    private String mappingRules;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}