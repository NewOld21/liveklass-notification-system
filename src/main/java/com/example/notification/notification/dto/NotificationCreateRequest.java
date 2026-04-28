package com.example.notification.notification.dto;

import java.util.Map;

import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Notification creation request")
public record NotificationCreateRequest(
        @Schema(description = "Notification recipient user ID. It must match the authenticated JWT user ID.", example = "101")
        @NotNull(message = "recipientId is required")
        @Positive(message = "recipientId must be positive")
        Long recipientId,

        @Schema(description = "Notification business type", example = "PAYMENT_CONFIRMED")
        @NotNull(message = "type is required")
        NotificationType type,

        @Schema(description = "Notification delivery channel", example = "EMAIL")
        @NotNull(message = "channel is required")
        NotificationChannel channel,

        @Schema(
                description = "Notification payload. eventId is required for dedupKey generation.",
                example = """
                        {
                          "eventId": "payment-5001",
                          "courseId": 301,
                          "paymentId": 5001
                        }
                        """
        )
        @NotEmpty(message = "payload is required")
        Map<String, Object> payload
) {
}
