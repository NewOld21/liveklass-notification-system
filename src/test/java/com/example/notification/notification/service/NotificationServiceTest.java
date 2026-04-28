package com.example.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.idempotency.entity.IdempotencyRecord;
import com.example.notification.idempotency.repository.IdempotencyStore;
import com.example.notification.idempotency.service.SimpleIdempotencyKeyGenerator;
import com.example.notification.notification.dto.NotificationCreateRequest;
import com.example.notification.notification.dto.NotificationCreateResponse;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationTemplateRepository notificationTemplateRepository;
    private IdempotencyStore idempotencyStore;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        notificationTemplateRepository = org.mockito.Mockito.mock(NotificationTemplateRepository.class);
        idempotencyStore = org.mockito.Mockito.mock(IdempotencyStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                notificationTemplateRepository,
                idempotencyStore,
                new SimpleIdempotencyKeyGenerator(),
                new ObjectMapper(),
                clock
        );
    }

    @Test
    @DisplayName("알림 생성 요청이 유효하면 PENDING 알림을 저장한다")
    void createSavesPendingNotification() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001", "paymentId", 5001)
        );
        User user = User.create("user@test.com", "tester", "encoded-password");
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "title",
                "body",
                true
        );

        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationTemplateRepository.findByTypeAndChannel(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL
        )).thenReturn(Optional.of(template));
        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001")).thenReturn(Optional.empty());
        when(idempotencyStore.saveProcessingIfAbsent(
                org.mockito.Mockito.eq("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"),
                any(Duration.class)
        )).thenReturn(true);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 1L);
            return notification;
        });

        NotificationCreateResponse response = notificationService.create(request);

        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(response.requestedAt()).isEqualTo("2026-04-24T10:00:00");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo(user);
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT_CONFIRMED);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getDedupKey()).isEqualTo("101:PAYMENT_CONFIRMED:EMAIL:payment-5001");
        assertThat(saved.getPayload()).contains("\"eventId\":\"payment-5001\"");
        assertThat(saved.getMaxRetryCount()).isEqualTo(3);
        verify(idempotencyStore).saveCompleted(
                org.mockito.Mockito.eq("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"),
                org.mockito.Mockito.eq(1L),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("수신자가 없으면 알림 생성에 실패한다")
    void createFailsWhenRecipientDoesNotExist() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                999L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        when(idempotencyStore.find("999:PAYMENT_CONFIRMED:EMAIL:payment-5001")).thenReturn(Optional.empty());
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(idempotencyStore, never()).saveProcessingIfAbsent(any(String.class), any(Duration.class));
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("활성 템플릿이 없으면 알림 생성에 실패한다")
    void createFailsWhenActiveTemplateDoesNotExist() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        User user = User.create("user@test.com", "tester", "encoded-password");

        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001")).thenReturn(Optional.empty());
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationTemplateRepository.findByTypeAndChannel(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(idempotencyStore, never()).saveProcessingIfAbsent(any(String.class), any(Duration.class));
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("Redis에 처리 중인 동일 요청이 있으면 알림 생성에 실패한다")
    void createFailsWhenSameRequestIsProcessing() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"))
                .thenReturn(Optional.of(IdempotencyRecord.processing()));

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_REQUEST);

        verify(userRepository, never()).findById(101L);
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("Redis에 완료된 동일 요청이 있으면 기존 알림 응답을 반환한다")
    void createReturnsExistingNotificationWhenSameRequestIsCompleted() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        User user = User.create("user@test.com", "tester", "encoded-password");
        Notification notification = Notification.createPending(
                user,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                java.time.LocalDateTime.of(2026, 4, 24, 10, 0)
        );
        ReflectionTestUtils.setField(notification, "id", 1L);

        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"))
                .thenReturn(Optional.of(IdempotencyRecord.completed(1L)));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationCreateResponse response = notificationService.create(request);

        assertThat(response.notificationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        verify(userRepository, never()).findById(101L);
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("Redis 처리 중 상태 저장에 실패하면 중복 요청으로 차단한다")
    void createFailsWhenProcessingLockCannotBeSaved() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        User user = User.create("user@test.com", "tester", "encoded-password");
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "title",
                "body",
                true
        );

        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001")).thenReturn(Optional.empty());
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationTemplateRepository.findByTypeAndChannel(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL
        )).thenReturn(Optional.of(template));
        when(idempotencyStore.saveProcessingIfAbsent(
                org.mockito.Mockito.eq("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"),
                any(Duration.class)
        )).thenReturn(false);

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_REQUEST);

        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("DB dedupKey unique 제약에 걸리면 기존 알림 응답을 반환한다")
    void createReturnsExistingNotificationWhenDatabaseDedupKeyConstraintBlocksDuplicate() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
        User user = User.create("user@test.com", "tester", "encoded-password");
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "title",
                "body",
                true
        );
        Notification existingNotification = Notification.createPending(
                user,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                java.time.LocalDateTime.of(2026, 4, 24, 10, 0)
        );
        ReflectionTestUtils.setField(existingNotification, "id", 1L);

        when(idempotencyStore.find("101:PAYMENT_CONFIRMED:EMAIL:payment-5001")).thenReturn(Optional.empty());
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationTemplateRepository.findByTypeAndChannel(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL
        )).thenReturn(Optional.of(template));
        when(idempotencyStore.saveProcessingIfAbsent(
                org.mockito.Mockito.eq("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"),
                any(Duration.class)
        )).thenReturn(true);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("dedup_key unique violation"));
        when(notificationRepository.findByDedupKey("101:PAYMENT_CONFIRMED:EMAIL:payment-5001"))
                .thenReturn(Optional.of(existingNotification));

        NotificationCreateResponse response = notificationService.create(request);

        assertThat(response.notificationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        verify(idempotencyStore).clear("101:PAYMENT_CONFIRMED:EMAIL:payment-5001");
    }

    @Test
    @DisplayName("payload.eventId가 없으면 알림 생성에 실패한다")
    void createFailsWhenEventIdIsMissing() {
        NotificationCreateRequest request = new NotificationCreateRequest(
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("paymentId", 5001)
        );

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(idempotencyStore, never()).find(any(String.class));
        verify(userRepository, never()).findById(101L);
        verify(notificationTemplateRepository, never()).findByTypeAndChannel(
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL
        );
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("인증된 수신자는 알림 상태를 조회할 수 있다")
    void getStatusReturnsNotificationForAuthenticatedRecipient() {
        User user = createUserWithId(101L);
        Notification notification = createNotificationWithId(1L, user);
        ReflectionTestUtils.setField(notification, "retryCount", 1);
        ReflectionTestUtils.setField(notification, "nextRetryAt", java.time.LocalDateTime.of(2026, 4, 24, 10, 5));
        ReflectionTestUtils.setField(notification, "lastErrorCode", "EMAIL_TEMPORARY_FAILURE");
        ReflectionTestUtils.setField(notification, "lastErrorMessage", "mock smtp timeout");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationStatusResponse response = notificationService.getStatus(1L, 101L);

        assertThat(response.notificationId()).isEqualTo(1L);
        assertThat(response.recipientId()).isEqualTo(101L);
        assertThat(response.type()).isEqualTo(NotificationType.PAYMENT_CONFIRMED);
        assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.nextRetryAt()).isEqualTo("2026-04-24T10:05:00");
        assertThat(response.lastErrorCode()).isEqualTo("EMAIL_TEMPORARY_FAILURE");
        assertThat(response.lastErrorMessage()).isEqualTo("mock smtp timeout");
    }

    @Test
    @DisplayName("다른 사용자의 알림 상태 조회는 차단한다")
    void getStatusFailsWhenAuthenticatedUserIsNotRecipient() {
        User user = createUserWithId(101L);
        Notification notification = createNotificationWithId(1L, user);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.getStatus(1L, 202L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("없는 알림 상태 조회는 실패한다")
    void getStatusFailsWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getStatus(999L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("인증된 수신자는 알림을 읽음 처리할 수 있다")
    void markAsReadUpdatesReadAtForAuthenticatedRecipient() {
        User user = createUserWithId(101L);
        Notification notification = createNotificationWithId(1L, user);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 101L);

        assertThat(notification.getReadAt()).isEqualTo("2026-04-24T10:00:00");
    }

    @Test
    @DisplayName("이미 읽은 알림은 기존 읽음 시각을 유지한다")
    void markAsReadKeepsExistingReadAt() {
        User user = createUserWithId(101L);
        Notification notification = createNotificationWithId(1L, user);
        java.time.LocalDateTime existingReadAt = java.time.LocalDateTime.of(2026, 4, 24, 9, 0);
        ReflectionTestUtils.setField(notification, "readAt", existingReadAt);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 101L);

        assertThat(notification.getReadAt()).isEqualTo(existingReadAt);
    }

    @Test
    @DisplayName("다른 사용자의 알림 읽음 처리는 차단한다")
    void markAsReadFailsWhenAuthenticatedUserIsNotRecipient() {
        User user = createUserWithId(101L);
        Notification notification = createNotificationWithId(1L, user);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 202L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    private User createUserWithId(Long userId) {
        User user = User.create("user" + userId + "@test.com", "tester", "encoded-password");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Notification createNotificationWithId(Long notificationId, User user) {
        Notification notification = Notification.createPending(
                user,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                user.getId() + ":PAYMENT_CONFIRMED:EMAIL:payment-5001",
                "{\"eventId\":\"payment-5001\"}",
                3,
                java.time.LocalDateTime.of(2026, 4, 24, 10, 0)
        );
        ReflectionTestUtils.setField(notification, "id", notificationId);
        return notification;
    }
}
