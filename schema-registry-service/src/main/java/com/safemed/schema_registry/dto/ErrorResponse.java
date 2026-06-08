package com.safemed.schema_registry.dto;

import java.time.LocalDateTime;

// structured error body returned by the global handler
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String path
) {
}
