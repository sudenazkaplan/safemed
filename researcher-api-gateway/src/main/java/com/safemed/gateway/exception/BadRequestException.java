package com.safemed.gateway.exception;

// thrown for bad input: duplicate user, wrong password, dataset not ready
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
