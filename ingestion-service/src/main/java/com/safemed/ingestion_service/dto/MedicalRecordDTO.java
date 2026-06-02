package com.safemed.ingestion_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MedicalRecordDTO {

    @NotBlank(message = "Patient name must not be blank")
    @Size(max = 100, message = "Patient name must be at most 100 characters")
    private String patientName;

    @NotBlank(message = "National ID must not be blank")
    @Size(min = 11, max = 11, message = "National ID must be exactly 11 digits")
    private String nationalId;

    @NotBlank(message = "Disease info must not be blank")
    @Size(max = 500, message = "Disease info must be at most 500 characters")
    private String diseaseInfo;

    @NotBlank(message = "Hospital name must not be blank")
    @Size(max = 200, message = "Hospital name must be at most 200 characters")
    private String hospitalName;
}
