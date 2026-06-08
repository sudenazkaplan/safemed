package com.safemed.schema_registry.model;

import com.safemed.schema_registry.dto.HospitalSchemaDTO;
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

    // build a new entity from an incoming dto
    public static HospitalSchema fromDTO(HospitalSchemaDTO dto) {
        HospitalSchema entity = new HospitalSchema();
        entity.setInstitutionName(dto.getInstitutionName());
        entity.setFormatType(dto.getFormatType());
        entity.setMappingRules(dto.getMappingRules());
        entity.setActive(dto.isActive());
        return entity;
    }

    // expose this entity as a response dto
    public HospitalSchemaDTO toDTO() {
        HospitalSchemaDTO dto = new HospitalSchemaDTO();
        dto.setId(this.id);
        dto.setInstitutionName(this.institutionName);
        dto.setFormatType(this.formatType);
        dto.setMappingRules(this.mappingRules);
        dto.setActive(this.isActive);
        return dto;
    }
}