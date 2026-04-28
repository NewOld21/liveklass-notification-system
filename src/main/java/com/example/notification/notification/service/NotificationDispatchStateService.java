package com.example.notification.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.repository.NotificationRepository;

@Service
public class NotificationDispatchStateService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationDispatchStateService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean tryStartProcessing(Long notificationId) {
        LocalDateTime now = LocalDateTime.now(clock);

        int updated = notificationRepository.markPendingAsProcessing(notificationId);
        if (updated == 1) {
            return true;
        }

        return notificationRepository.markRetryWaitingAsProcessing(notificationId, now) == 1;
    }

    @Transactional
    public void markSent(Long notificationId) {
        Notification notification = findNotification(notificationId);
        notification.markSent(LocalDateTime.now(clock));
    }

    @Transactional
    public void markDispatchFailed(Long notificationId, String errorCode, String errorMessage) {
        Notification notification = findNotification(notificationId);
        notification.markDispatchFailed(errorCode, errorMessage, LocalDateTime.now(clock));
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found."));
    }
}
