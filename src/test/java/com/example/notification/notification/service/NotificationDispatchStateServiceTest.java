package com.example.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.user.entity.User;

class NotificationDispatchStateServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationDispatchStateService stateService;

    @BeforeEach
    void setUp() {
        notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        stateService = new NotificationDispatchStateService(notificationRepository, clock);
    }

    @Test
    @DisplayName("PENDING 상태 점유에 성공하면 PROCESSING 시작 성공을 반환한다")
    void tryStartProcessingReturnsTrueWhenPendingClaimSucceeds() {
        when(notificationRepository.markPendingAsProcessing(1L)).thenReturn(1);

        boolean result = stateService.tryStartProcessing(1L);

        assertThat(result).isTrue();
        verify(notificationRepository).markPendingAsProcessing(1L);
    }

    @Test
    @DisplayName("PENDING 점유 실패 후 RETRY_WAITING 점유에 성공하면 PROCESSING 시작 성공을 반환한다")
    void tryStartProcessingReturnsTrueWhenRetryWaitingClaimSucceeds() {
        when(notificationRepository.markPendingAsProcessing(1L)).thenReturn(0);
        when(notificationRepository.markRetryWaitingAsProcessing(
                org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.any()
        )).thenReturn(1);

        boolean result = stateService.tryStartProcessing(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("PENDING과 RETRY_WAITING 점유 모두 실패하면 PROCESSING 시작 실패를 반환한다")
    void tryStartProcessingReturnsFalseWhenClaimFails() {
        when(notificationRepository.markPendingAsProcessing(1L)).thenReturn(0);
        when(notificationRepository.markRetryWaitingAsProcessing(
                org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.any()
        )).thenReturn(0);

        boolean result = stateService.tryStartProcessing(1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("PROCESSING 알림을 SENT로 전이한다")
    void markSentTransitionsNotificationToSent() {
        Notification notification = createProcessingNotification();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        stateService.markSent(1L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getProcessedAt()).isEqualTo("2026-04-24T10:00:00");
    }

    @Test
    @DisplayName("PROCESSING 알림 실패를 기록하고 RETRY_WAITING으로 전이한다")
    void markDispatchFailedTransitionsNotificationToRetryWaiting() {
        Notification notification = createProcessingNotification();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        stateService.markDispatchFailed(1L, "EMAIL_TEMPORARY_FAILURE", "mock smtp timeout");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isEqualTo("2026-04-24T10:05:00");
    }

    private Notification createProcessingNotification() {
        Notification notification = Notification.createPending(
                User.create("user@test.com", "tester", "encoded-password"),
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                java.time.LocalDateTime.of(2026, 4, 24, 9, 0)
        );
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 30));
        return notification;
    }
}
