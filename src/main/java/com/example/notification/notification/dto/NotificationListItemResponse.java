package com.example.notification.notification.dto;

import java.time.LocalDateTime;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification list item")
public record NotificationListItemResponse(
        @Schema(description = "Notification ID", example = "1")
        Long notificationId,

        @Schema(description = "Recipient user ID", example = "101")
        Long recipientId,

        @Schema(description = "Notification business type", example = "PAYMENT_CONFIRMED")
        NotificationType type,

        @Schema(description = "Notification delivery channel", example = "EMAIL")
        NotificationChannel channel,

        @Schema(description = "Current notification status", example = "PENDING")
        NotificationStatus status,

        @Schema(description = "Notification request time", example = "2026-04-24T10:00:00")
        LocalDateTime requestedAt,

        @Schema(description = "Read time. Null means unread.", example = "2026-04-24T10:05:00")
        LocalDateTime readAt
) {

    public static NotificationListItemResponse from(Notification notification) {
        return new NotificationListItemResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getRequestedAt(),
                notification.getReadAt()
        );
    }
}
