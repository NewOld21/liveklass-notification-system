package com.example.notification.idempotency.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.notification.idempotency.entity.IdempotencyRecord;
import com.example.notification.idempotency.entity.IdempotencyStatus;
import com.example.notification.idempotency.repository.IdempotencyStore;

@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PROCESSING_VALUE = IdempotencyStatus.PROCESSING.name();
    private static final String COMPLETED_PREFIX = IdempotencyStatus.COMPLETED.name() + ":";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (PROCESSING_VALUE.equals(value)) {
            return Optional.of(IdempotencyRecord.processing());
        }
        if (value.startsWith(COMPLETED_PREFIX)) {
            Long notificationId = Long.parseLong(value.substring(COMPLETED_PREFIX.length()));
            return Optional.of(IdempotencyRecord.completed(notificationId));
        }
        return Optional.empty();
    }

    @Override
    public void saveProcessing(String key, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, PROCESSING_VALUE, ttl);
    }

    @Override
    public void saveCompleted(String key, Long notificationId, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, COMPLETED_PREFIX + notificationId, ttl);
    }

    @Override
    public void clear(String key) {
        stringRedisTemplate.delete(key);
    }
}
