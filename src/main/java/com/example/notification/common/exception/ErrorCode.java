package com.example.notification.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-400", "Invalid request."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "Resource not found."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "COMMON-409", "Duplicate request."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-409", "Email already exists."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-401", "Invalid credentials."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-401-INVALID", "Invalid authentication token."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-401-EXPIRED", "Expired authentication token."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-403", "Access denied."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "Internal server error.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
