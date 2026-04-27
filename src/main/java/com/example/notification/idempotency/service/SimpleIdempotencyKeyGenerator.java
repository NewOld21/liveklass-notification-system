package com.example.notification.idempotency.service;

import org.springframework.stereotype.Component;

import com.example.notification.idempotency.service.IdempotencyKeyGenerator;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;

@Component
public class SimpleIdempotencyKeyGenerator implements IdempotencyKeyGenerator {

    @Override
    public String generate(Long recipientId, NotificationType type, NotificationChannel channel, String eventId) {
        return "%d:%s:%s:%s".formatted(recipientId, type.name(), channel.name(), eventId);
    }
}
