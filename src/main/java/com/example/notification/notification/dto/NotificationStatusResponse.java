package com.example.notification.notification.dto;

import java.time.LocalDateTime;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification status response")
public record NotificationStatusResponse(
        @Schema(description = "Notification ID", example = "1")
        Long notificationId,

        @Schema(description = "Recipient user ID", example = "101")
        Long recipientId,

        @Schema(description = "Notification business type", example = "PAYMENT_CONFIRMED")
        NotificationType type,

        @Schema(description = "Notification delivery channel", example = "EMAIL")
        NotificationChannel channel,

        @Schema(description = "Current notification status", example = "RETRY_WAITING")
        NotificationStatus status,

        @Schema(description = "Current retry count", example = "1")
        int retryCount,

        @Schema(description = "Next retry time", example = "2026-04-24T10:05:00")
        LocalDateTime nextRetryAt,

        @Schema(description = "Last dispatch error code", example = "EMAIL_TEMPORARY_FAILURE")
        String lastErrorCode,

        @Schema(description = "Last dispatch error message", example = "mock smtp timeout")
        String lastErrorMessage
) {

    public static NotificationStatusResponse from(Notification notification) {
        return new NotificationStatusResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getNextRetryAt(),
                notification.getLastErrorCode(),
                notification.getLastErrorMessage()
        );
    }
}
