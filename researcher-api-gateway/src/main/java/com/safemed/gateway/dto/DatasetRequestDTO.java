package com.safemed.gateway.dto;

import lombok.Data;

// body for creating a new dataset query
@Data
public class DatasetRequestDTO {
    private Integer ageMin;
    private Integer ageMax;
    private String diseaseType;
}
