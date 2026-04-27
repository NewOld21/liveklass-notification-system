package com.example.notification.idempotency.entity;

public record IdempotencyRecord(
        IdempotencyStatus status,
        Long notificationId
) {

    public static IdempotencyRecord processing() {
        return new IdempotencyRecord(IdempotencyStatus.PROCESSING, null);
    }

    public static IdempotencyRecord completed(Long notificationId) {
        return new IdempotencyRecord(IdempotencyStatus.COMPLETED, notificationId);
    }
}
