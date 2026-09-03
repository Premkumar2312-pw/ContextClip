package com.contextclip.exception;

public class AiServiceException extends RuntimeException {

    private final int statusCode;

    public AiServiceException(String message) {
        this(message, 502);
    }

    public AiServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiServiceException(String message, Throwable cause) {
        this(message, 502, cause);
    }

    public AiServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}


