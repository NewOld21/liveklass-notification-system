package com.example.notification.common.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        String field,
        LocalDateTime timestamp
) {

    public static ErrorResponse of(String code, String message, LocalDateTime timestamp) {
        return new ErrorResponse(code, message, null, timestamp);
    }

    public static ErrorResponse of(String code, String message, String field, LocalDateTime timestamp) {
        return new ErrorResponse(code, message, field, timestamp);
    }
}
