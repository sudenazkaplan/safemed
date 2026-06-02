package com.safemed.ingestion_service.dto;

import java.time.Instant;

public record ErrorResponseDTO(
        Instant timestamp,
        int status,
        String error,
        String path
) {
}
