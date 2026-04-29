package com.example.notification.notification.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.notification.service.NotificationDispatchService;
import com.example.notification.notification.service.NotificationProcessingPolicy;

@Component
@ConditionalOnProperty(
        name = "notification.dispatch.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchService dispatchService;
    private final Clock clock;
    private final int batchSize;

    public NotificationDispatchScheduler(
            NotificationRepository notificationRepository,
            NotificationDispatchService dispatchService,
            Clock clock,
            @Value("${notification.dispatch.scheduler.batch-size:50}") int batchSize
    ) {
        this.notificationRepository = notificationRepository;
        this.dispatchService = dispatchService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.dispatch.scheduler.fixed-delay-ms:5000}")
    public void dispatchPendingAndRetryWaitingNotifications() {
        PageRequest limit = PageRequest.of(0, batchSize);
        recoverStaleProcessingTargets(limit);
        dispatchTargets(notificationRepository.findPendingDispatchTargetIds(limit));
        dispatchTargets(notificationRepository.findRetryWaitingDispatchTargetIds(LocalDateTime.now(clock), limit));
    }

    private void recoverStaleProcessingTargets(PageRequest limit) {
        Duration threshold = NotificationProcessingPolicy.STALE_PROCESSING_THRESHOLD;
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(threshold);
        for (Long notificationId : notificationRepository.findStaleProcessingTargetIds(staleBefore, limit)) {
            try {
                dispatchService.recoverStaleProcessing(notificationId);
            } catch (RuntimeException ex) {
                log.error("Stale notification recovery failed unexpectedly. notificationId={}", notificationId, ex);
            }
        }
    }

    private void dispatchTargets(List<Long> notificationIds) {
        for (Long notificationId : notificationIds) {
            try {
                dispatchService.dispatch(notificationId);
            } catch (RuntimeException ex) {
                log.error("Notification dispatch failed unexpectedly. notificationId={}", notificationId, ex);
            }
        }
    }
}
