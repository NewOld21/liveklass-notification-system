package com.example.notification.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.user.entity.User;

class NotificationStateTransitionTest {

    @Test
    @DisplayName("PENDING 알림은 PROCESSING으로 전이할 수 있다")
    void pendingCanStartProcessing() {
        Notification notification = createNotification();

        notification.startProcessing(LocalDateTime.of(2026, 4, 24, 10, 0));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING 알림은 SENT로 전이할 수 있다")
    void processingCanBeMarkedAsSent() {
        Notification notification = createNotification();
        LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
        notification.startProcessing(now);

        notification.markSent(now.plusMinutes(1));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getProcessedAt()).isEqualTo("2026-04-24T10:01:00");
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getLastErrorCode()).isNull();
        assertThat(notification.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("PROCESSING 실패 시 재시도 가능하면 RETRY_WAITING으로 전이한다")
    void processingFailureCanBeMarkedAsRetryWaiting() {
        Notification notification = createNotification();
        LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
        notification.startProcessing(now);

        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", now.plusMinutes(1));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isEqualTo("2026-04-24T10:06:00");
        assertThat(notification.getLastErrorCode()).isEqualTo("EMAIL_TEMPORARY_FAILURE");
        assertThat(notification.getLastErrorMessage()).isEqualTo("mock smtp timeout");
    }

    @Test
    @DisplayName("RETRY_WAITING 알림은 재시도 시각 이후 PROCESSING으로 전이할 수 있다")
    void retryWaitingCanStartProcessingWhenDue() {
        Notification notification = createNotification();
        LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
        notification.startProcessing(now);
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", now);

        notification.startProcessing(LocalDateTime.of(2026, 4, 24, 10, 5));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    @DisplayName("재시도 횟수를 초과한 PROCESSING 실패는 FAILED로 전이한다")
    void processingFailureExceedingRetriesCanBeMarkedAsFailed() {
        Notification notification = createNotification();
        LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);

        failAndRetry(notification, now, 0);
        failAndRetry(notification, now, 5);
        failAndRetry(notification, now, 15);

        notification.startProcessing(LocalDateTime.of(2026, 4, 24, 10, 35));
        notification.markDispatchFailed("EMAIL_PERMANENT_FAILURE", "mailbox disabled", LocalDateTime.of(2026, 4, 24, 10, 36));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(4);
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getLastErrorCode()).isEqualTo("EMAIL_PERMANENT_FAILURE");
        assertThat(notification.getLastErrorMessage()).isEqualTo("mailbox disabled");
    }

    @Test
    @DisplayName("PROCESSING이 아닌 알림은 SENT나 실패 상태로 전이할 수 없다")
    void nonProcessingCannotBeCompleted() {
        Notification notification = createNotification();

        assertThatThrownBy(() -> notification.markSent(LocalDateTime.of(2026, 4, 24, 10, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> notification.markDispatchFailed("ERROR", "message", LocalDateTime.of(2026, 4, 24, 10, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private void failAndRetry(Notification notification, LocalDateTime baseTime, int failureMinuteOffset) {
        LocalDateTime failureTime = baseTime.plusMinutes(failureMinuteOffset);
        notification.startProcessing(failureTime);
        notification.markDispatchFailed("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout", failureTime);
    }

    private Notification createNotification() {
        return Notification.createPending(
                User.create("user@test.com", "tester", "encoded-password"),
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                LocalDateTime.of(2026, 4, 24, 9, 0)
        );
    }
}
