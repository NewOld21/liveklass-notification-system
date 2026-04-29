package com.example.notification.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.notification.dto.AdminFailedNotificationResponse;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.repository.NotificationRepository;

@Service
public class AdminNotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public AdminNotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminFailedNotificationResponse> getExhaustedFailedNotifications(int size) {
        int resolvedSize = Math.max(1, Math.min(size, 100));
        return notificationRepository.findExhaustedFailedNotifications(PageRequest.of(0, resolvedSize)).stream()
                .map(AdminFailedNotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationStatusResponse reopenForManualRetry(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found."));

        notification.reopenForManualRetry(LocalDateTime.now(clock));
        return NotificationStatusResponse.from(notification);
    }
}
