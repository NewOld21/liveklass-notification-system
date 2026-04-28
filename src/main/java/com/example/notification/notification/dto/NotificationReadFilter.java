package com.example.notification.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification read filter")
public enum NotificationReadFilter {
    ALL,
    READ,
    UNREAD
}
