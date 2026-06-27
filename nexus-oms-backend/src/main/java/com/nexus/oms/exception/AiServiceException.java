package com.nexus.oms.exception;

public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super("AI Service error: " + message);
    }

    public AiServiceException(String message, Throwable cause) {
        super("AI Service error: " + message, cause);
    }
}
