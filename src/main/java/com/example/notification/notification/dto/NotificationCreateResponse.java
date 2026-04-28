package com.example.notification.notification.dto;

import java.time.LocalDateTime;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification creation response")
public record NotificationCreateResponse(
        @Schema(description = "Created notification ID", example = "1")
        Long notificationId,

        @Schema(description = "Initial notification status", example = "PENDING")
        NotificationStatus status,

        @Schema(description = "Notification request time", example = "2026-04-24T10:00:00")
        LocalDateTime requestedAt
) {

    public static NotificationCreateResponse from(Notification notification) {
        return new NotificationCreateResponse(
                notification.getId(),
                notification.getStatus(),
                notification.getRequestedAt()
        );
    }
}
