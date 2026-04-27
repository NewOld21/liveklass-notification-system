package com.example.notification.common.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {

    public static ErrorResponse of(String code, String message, LocalDateTime timestamp) {
        return new ErrorResponse(code, message, timestamp);
    }
}
