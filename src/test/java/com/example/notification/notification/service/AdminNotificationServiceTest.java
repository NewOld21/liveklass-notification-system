package com.example.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.user.entity.User;

class AdminNotificationServiceTest {

    @Test
    @DisplayName("운영자는 최종 실패 알림을 수동 재시도 가능 상태로 되돌릴 수 있다")
    void reopenForManualRetryResetsRetryCountAndMovesToRetryWaiting() {
        NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        AdminNotificationService service = new AdminNotificationService(notificationRepository, clock);
        Notification notification = failedNotification();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationStatusResponse response = service.reopenForManualRetry(1L);

        assertThat(response.status()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(response.retryCount()).isZero();
        assertThat(response.nextRetryAt()).isEqualTo("2026-04-24T10:00:00");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(notification.getRetryCount()).isZero();
    }

    private Notification failedNotification() {
        User user = User.create("user@test.com", "tester", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 101L);
        Notification notification = Notification.createPending(
                user,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                java.time.LocalDateTime.of(2026, 4, 24, 9, 0)
        );
        ReflectionTestUtils.setField(notification, "id", 1L);
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 1));
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", java.time.LocalDateTime.of(2026, 4, 24, 9, 1));
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 6));
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", java.time.LocalDateTime.of(2026, 4, 24, 9, 6));
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 16));
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", java.time.LocalDateTime.of(2026, 4, 24, 9, 16));
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 36));
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", java.time.LocalDateTime.of(2026, 4, 24, 9, 36));
        return notification;
    }
}
