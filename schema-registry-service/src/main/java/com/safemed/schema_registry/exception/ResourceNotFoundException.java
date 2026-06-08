package com.safemed.schema_registry.exception;

// thrown when a schema id doesn't exist
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
