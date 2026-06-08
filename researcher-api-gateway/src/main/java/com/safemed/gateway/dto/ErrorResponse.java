package com.safemed.gateway.dto;

import java.time.LocalDateTime;

// standard error body returned by the global handler
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String path
) {
}
