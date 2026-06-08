package com.safemed.schema_registry.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// api payload, keeps the jpa entity out of the controller
@Data
public class HospitalSchemaDTO {

    // null on create, filled in on responses
    private Long id;

    @NotBlank(message = "institutionName is required")
    private String institutionName;

    // expected: HL7 / FHIR / JSON
    @NotBlank(message = "formatType is required")
    private String formatType;

    @NotBlank(message = "mappingRules is required")
    private String mappingRules;

    // active by default unless explicitly disabled
    private boolean isActive = true;
}
