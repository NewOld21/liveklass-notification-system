package com.example.notification.notification.dto;

import java.time.LocalDateTime;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin failed notification response")
public record AdminFailedNotificationResponse(
        Long notificationId,
        Long recipientId,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        int retryCount,
        int maxRetryCount,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime processedAt
) {

    public static AdminFailedNotificationResponse from(Notification notification) {
        return new AdminFailedNotificationResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getMaxRetryCount(),
                notification.getLastErrorCode(),
                notification.getLastErrorMessage(),
                notification.getProcessedAt()
        );
    }
}
