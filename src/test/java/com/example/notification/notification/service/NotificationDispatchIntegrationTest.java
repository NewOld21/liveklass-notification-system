package com.example.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.dispatchhistory.entity.DispatchStatus;
import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;
import com.example.notification.dispatchhistory.repository.NotificationDispatchHistoryRepository;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationDispatchIntegrationTest {

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDispatchHistoryRepository dispatchHistoryRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("저장된 PENDING 알림을 실제 발송 서비스로 처리하면 SENT와 성공 이력이 저장된다")
    void dispatchPendingNotificationStoresSuccessHistoryAndSentStatus() {
        User user = userRepository.save(User.create("dispatch-user@test.com", "tester", "password-hash"));
        templateRepository.findByTypeAndChannel(NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL)
                .orElseGet(() -> templateRepository.save(NotificationTemplate.create(
                        NotificationType.PAYMENT_CONFIRMED,
                        NotificationChannel.EMAIL,
                        "Payment confirmed",
                        "Your payment has been confirmed.",
                        true
                )));
        Notification notification = notificationRepository.saveAndFlush(Notification.createPending(
                user,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "dispatch-user:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                LocalDateTime.of(2026, 4, 24, 10, 0)
        ));

        boolean dispatched = dispatchService.dispatch(notification.getId());
        notificationRepository.flush();

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        List<NotificationDispatchHistory> histories = dispatchHistoryRepository.findByNotificationIdOrderByAttemptAsc(
                notification.getId()
        );

        assertThat(dispatched).isTrue();
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(updated.getProcessedAt()).isNotNull();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getAttempt()).isEqualTo(1);
        assertThat(histories.get(0).getStatus()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(histories.get(0).getErrorCode()).isNull();
        assertThat(histories.get(0).getErrorMessage()).isNull();
    }
}
