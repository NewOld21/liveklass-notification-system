package com.example.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.notification.dispatchhistory.entity.DispatchStatus;
import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;
import com.example.notification.dispatchhistory.repository.NotificationDispatchHistoryRepository;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.notification.sender.NotificationSendResult;
import com.example.notification.notification.sender.NotificationSender;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;
import com.example.notification.user.entity.User;

class NotificationDispatchServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationTemplateRepository templateRepository;
    private NotificationDispatchHistoryRepository dispatchHistoryRepository;
    private NotificationDispatchStateService stateService;
    private FakeNotificationSender sender;
    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        templateRepository = org.mockito.Mockito.mock(NotificationTemplateRepository.class);
        dispatchHistoryRepository = org.mockito.Mockito.mock(NotificationDispatchHistoryRepository.class);
        stateService = org.mockito.Mockito.mock(NotificationDispatchStateService.class);
        sender = new FakeNotificationSender(NotificationChannel.EMAIL);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        dispatchService = new NotificationDispatchService(
                notificationRepository,
                templateRepository,
                dispatchHistoryRepository,
                stateService,
                List.of(sender),
                clock
        );
    }

    @Test
    @DisplayName("알림 점유에 실패하면 발송하지 않는다")
    void dispatchReturnsFalseWhenProcessingClaimFails() {
        when(stateService.tryStartProcessing(1L)).thenReturn(false);

        boolean dispatched = dispatchService.dispatch(1L);

        assertThat(dispatched).isFalse();
        verify(notificationRepository, never()).findById(1L);
        verify(dispatchHistoryRepository, never()).save(any(NotificationDispatchHistory.class));
        verify(stateService, never()).markSent(1L);
        verify(stateService, never()).markDispatchFailed(any(Long.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("발송 성공 시 성공 이력을 저장하고 SENT로 전이한다")
    void dispatchSavesSuccessHistoryAndMarksSent() {
        Notification notification = createNotification(1L);
        NotificationTemplate template = createTemplate();
        sender.result = NotificationSendResult.success();
        when(stateService.tryStartProcessing(1L)).thenReturn(true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(templateRepository.findByTypeAndChannel(NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(template));

        boolean dispatched = dispatchService.dispatch(1L);

        assertThat(dispatched).isTrue();
        ArgumentCaptor<NotificationDispatchHistory> captor = ArgumentCaptor.forClass(NotificationDispatchHistory.class);
        verify(dispatchHistoryRepository).save(captor.capture());
        NotificationDispatchHistory history = captor.getValue();
        assertThat(history.getNotification()).isEqualTo(notification);
        assertThat(history.getAttempt()).isEqualTo(1);
        assertThat(history.getStatus()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(history.getErrorCode()).isNull();
        assertThat(history.getErrorMessage()).isNull();
        assertThat(history.getDispatchedAt()).isEqualTo("2026-04-24T10:00:00");
        verify(stateService).markSent(1L);
        verify(stateService, never()).markDispatchFailed(any(Long.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("발송 실패 시 실패 이력을 저장하고 실패 상태 전이를 호출한다")
    void dispatchSavesFailureHistoryAndMarksDispatchFailed() {
        Notification notification = createNotification(1L);
        ReflectionTestUtils.setField(notification, "retryCount", 1);
        NotificationTemplate template = createTemplate();
        sender.result = NotificationSendResult.failure("EMAIL_TEMPORARY_FAILURE", "mock smtp timeout");
        when(stateService.tryStartProcessing(1L)).thenReturn(true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(templateRepository.findByTypeAndChannel(NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(template));

        boolean dispatched = dispatchService.dispatch(1L);

        assertThat(dispatched).isTrue();
        ArgumentCaptor<NotificationDispatchHistory> captor = ArgumentCaptor.forClass(NotificationDispatchHistory.class);
        verify(dispatchHistoryRepository).save(captor.capture());
        NotificationDispatchHistory history = captor.getValue();
        assertThat(history.getAttempt()).isEqualTo(2);
        assertThat(history.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(history.getErrorCode()).isEqualTo("EMAIL_TEMPORARY_FAILURE");
        assertThat(history.getErrorMessage()).isEqualTo("mock smtp timeout");
        verify(stateService).markDispatchFailed(1L, "EMAIL_TEMPORARY_FAILURE", "mock smtp timeout");
        verify(stateService, never()).markSent(1L);
    }

    @Test
    @DisplayName("활성 템플릿이 없으면 발송 실패 이력을 저장한다")
    void dispatchFailsWhenActiveTemplateDoesNotExist() {
        Notification notification = createNotification(1L);
        when(stateService.tryStartProcessing(1L)).thenReturn(true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(templateRepository.findByTypeAndChannel(NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        boolean dispatched = dispatchService.dispatch(1L);

        assertThat(dispatched).isTrue();
        ArgumentCaptor<NotificationDispatchHistory> captor = ArgumentCaptor.forClass(NotificationDispatchHistory.class);
        verify(dispatchHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(captor.getValue().getErrorCode()).isEqualTo("TEMPLATE_NOT_FOUND");
        verify(stateService).markDispatchFailed(1L, "TEMPLATE_NOT_FOUND", "Active notification template not found.");
    }

    @Test
    @DisplayName("채널 sender가 없으면 발송 실패 이력을 저장한다")
    void dispatchFailsWhenSenderDoesNotExist() {
        Notification notification = createNotification(1L);
        NotificationTemplate template = createTemplate();
        NotificationDispatchService serviceWithoutSender = new NotificationDispatchService(
                notificationRepository,
                templateRepository,
                dispatchHistoryRepository,
                stateService,
                List.of(),
                Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        when(stateService.tryStartProcessing(1L)).thenReturn(true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(templateRepository.findByTypeAndChannel(NotificationType.PAYMENT_CONFIRMED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(template));

        boolean dispatched = serviceWithoutSender.dispatch(1L);

        assertThat(dispatched).isTrue();
        verify(stateService).markDispatchFailed(1L, "SENDER_NOT_FOUND", "Notification sender not found.");
    }

    @Test
    @DisplayName("오래된 PROCESSING 알림은 실패 이력을 저장하고 재시도 대기 상태로 복구한다")
    void recoverStaleProcessingStoresFailureHistoryAndRetryWaitingStatus() {
        Notification notification = createNotification(1L);
        notification.startProcessing(java.time.LocalDateTime.of(2026, 4, 24, 9, 30));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        boolean recovered = dispatchService.recoverStaleProcessing(1L);

        assertThat(recovered).isTrue();
        ArgumentCaptor<NotificationDispatchHistory> captor = ArgumentCaptor.forClass(NotificationDispatchHistory.class);
        verify(dispatchHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(captor.getValue().getErrorCode()).isEqualTo("STALE_PROCESSING_TIMEOUT");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isEqualTo("2026-04-24T10:05:00");
    }

    private Notification createNotification(Long id) {
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
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    private NotificationTemplate createTemplate() {
        return NotificationTemplate.create(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "Payment confirmed",
                "Your payment has been confirmed.",
                true
        );
    }

    private static class FakeNotificationSender implements NotificationSender {

        private final NotificationChannel channel;
        private NotificationSendResult result = NotificationSendResult.success();

        private FakeNotificationSender(NotificationChannel channel) {
            this.channel = channel;
        }

        @Override
        public boolean supports(NotificationChannel channel) {
            return this.channel == channel;
        }

        @Override
        public NotificationSendResult send(Notification notification, NotificationTemplate template) {
            return result;
        }
    }
}
