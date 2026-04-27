package com.example.notification.idempotency.repository;

import java.time.Duration;
import java.util.Optional;

import com.example.notification.idempotency.entity.IdempotencyRecord;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    void saveProcessing(String key, Duration ttl);

    void saveCompleted(String key, Long notificationId, Duration ttl);

    void clear(String key);
}
