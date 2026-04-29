package com.example.notification.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.dispatchhistory.entity.DispatchStatus;
import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;
import com.example.notification.dispatchhistory.repository.NotificationDispatchHistoryRepository;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.notification.sender.NotificationSendResult;
import com.example.notification.notification.sender.NotificationSender;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;

@Service
public class NotificationDispatchService {

    private static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    private static final String SENDER_NOT_FOUND = "SENDER_NOT_FOUND";
    private static final String SENDER_EXCEPTION = "SENDER_EXCEPTION";
    private static final String STALE_PROCESSING_TIMEOUT = "STALE_PROCESSING_TIMEOUT";

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationDispatchHistoryRepository dispatchHistoryRepository;
    private final NotificationDispatchStateService stateService;
    private final List<NotificationSender> senders;
    private final Clock clock;

    public NotificationDispatchService(
            NotificationRepository notificationRepository,
            NotificationTemplateRepository notificationTemplateRepository,
            NotificationDispatchHistoryRepository dispatchHistoryRepository,
            NotificationDispatchStateService stateService,
            List<NotificationSender> senders,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.dispatchHistoryRepository = dispatchHistoryRepository;
        this.stateService = stateService;
        this.senders = senders;
        this.clock = clock;
    }

    public boolean dispatch(Long notificationId) {
        if (!stateService.tryStartProcessing(notificationId)) {
            return false;
        }

        Notification notification = findNotification(notificationId);
        int attempt = notification.getRetryCount() + 1;
        NotificationSendResult result = send(notification);

        saveHistory(notification, attempt, result);
        if (result.successful()) {
            stateService.markSent(notificationId);
            return true;
        }

        stateService.markDispatchFailed(notificationId, result.errorCode(), result.errorMessage());
        return true;
    }

    @Transactional
    public boolean recoverStaleProcessing(Long notificationId) {
        Notification notification = findNotification(notificationId);
        if (notification.getStatus() != com.example.notification.notification.entity.NotificationStatus.PROCESSING) {
            return false;
        }

        int attempt = notification.getRetryCount() + 1;
        NotificationSendResult result = NotificationSendResult.failure(
                STALE_PROCESSING_TIMEOUT,
                "Processing state exceeded stale threshold."
        );
        saveHistory(notification, attempt, result);
        notification.markDispatchFailed(result.errorCode(), result.errorMessage(), LocalDateTime.now(clock));
        return true;
    }

    private NotificationSendResult send(Notification notification) {
        NotificationTemplate template = notificationTemplateRepository.findByTypeAndChannel(
                        notification.getType(),
                        notification.getChannel()
                )
                .filter(NotificationTemplate::isActive)
                .orElse(null);
        if (template == null) {
            return NotificationSendResult.failure(TEMPLATE_NOT_FOUND, "Active notification template not found.");
        }

        NotificationSender sender = senders.stream()
                .filter(candidate -> candidate.supports(notification.getChannel()))
                .findFirst()
                .orElse(null);
        if (sender == null) {
            return NotificationSendResult.failure(SENDER_NOT_FOUND, "Notification sender not found.");
        }

        try {
            return sender.send(notification, template);
        } catch (RuntimeException ex) {
            return NotificationSendResult.failure(SENDER_EXCEPTION, ex.getMessage());
        }
    }

    protected void saveHistory(Notification notification, int attempt, NotificationSendResult result) {
        DispatchStatus status = result.successful() ? DispatchStatus.SUCCESS : DispatchStatus.FAILED;
        NotificationDispatchHistory history = NotificationDispatchHistory.create(
                notification,
                attempt,
                status,
                result.errorCode(),
                result.errorMessage(),
                LocalDateTime.now(clock)
        );
        dispatchHistoryRepository.save(history);
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found."));
    }
}
