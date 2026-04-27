package com.example.notification.idempotency.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;

class SimpleIdempotencyKeyGeneratorTest {

    private final SimpleIdempotencyKeyGenerator generator = new SimpleIdempotencyKeyGenerator();

    @Test
    void generateBuildsExpectedCompositeKey() {
        String key = generator.generate(101L, NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL, "payment-5001");

        assertThat(key).isEqualTo("101:PAYMENT_CONFIRMED:EMAIL:payment-5001");
    }
}
