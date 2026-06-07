package com.safemed.audit_compliance_service.exception;

// thrown when no logs exist for a given trackingId
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
