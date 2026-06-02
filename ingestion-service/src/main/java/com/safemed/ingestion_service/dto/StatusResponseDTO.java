package com.safemed.ingestion_service.dto;

public record StatusResponseDTO(
        String trackingId,
        String status,
        String message
) {
}
