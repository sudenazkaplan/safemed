package com.safemed.gateway.exception;

// thrown when a user / request / token is not found
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
