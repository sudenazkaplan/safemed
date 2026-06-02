package com.safemed.ingestion_service.dto;

import java.time.Instant;

public record RecordMetadataDTO(
        String trackingId,
        String patientName,
        String hospitalName,
        Instant receivedAt,
        String status
) {
}
