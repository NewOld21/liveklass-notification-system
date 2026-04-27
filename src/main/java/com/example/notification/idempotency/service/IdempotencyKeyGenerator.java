package com.example.notification.idempotency.service;

import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;

public interface IdempotencyKeyGenerator {

    String generate(Long recipientId, NotificationType type, NotificationChannel channel, String eventId);
}
