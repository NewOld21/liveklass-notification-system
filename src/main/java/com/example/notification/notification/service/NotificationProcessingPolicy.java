package com.example.notification.notification.service;

import java.time.Duration;
import java.util.List;

public final class NotificationProcessingPolicy {

    public static final int MAX_RETRY_COUNT = 3;
    public static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(10);
    public static final List<Duration> RETRY_BACKOFFS = List.of(
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(20)
    );

    private NotificationProcessingPolicy() {
    }

    public static Duration retryBackoff(int retryCount) {
        if (retryCount <= 0 || retryCount > RETRY_BACKOFFS.size()) {
            throw new IllegalArgumentException("retryCount must be between 1 and " + RETRY_BACKOFFS.size());
        }
        return RETRY_BACKOFFS.get(retryCount - 1);
    }
}
