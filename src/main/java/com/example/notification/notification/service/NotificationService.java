package com.example.notification.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.idempotency.entity.IdempotencyRecord;
import com.example.notification.idempotency.entity.IdempotencyStatus;
import com.example.notification.idempotency.repository.IdempotencyStore;
import com.example.notification.idempotency.service.IdempotencyKeyGenerator;
import com.example.notification.notification.dto.NotificationCreateRequest;
import com.example.notification.notification.dto.NotificationCreateResponse;
import com.example.notification.notification.dto.NotificationListItemResponse;
import com.example.notification.notification.dto.NotificationReadFilter;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final IdempotencyStore idempotencyStore;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationTemplateRepository notificationTemplateRepository,
            IdempotencyStore idempotencyStore,
            IdempotencyKeyGenerator idempotencyKeyGenerator,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.idempotencyStore = idempotencyStore;
        this.idempotencyKeyGenerator = idempotencyKeyGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public NotificationCreateResponse create(NotificationCreateRequest request) {
        log.info(
                "Notification create requested. recipientId={}, type={}, channel={}",
                request.recipientId(),
                request.type(),
                request.channel()
        );

        String eventId = extractEventId(request);
        String dedupKey = idempotencyKeyGenerator.generate(
                request.recipientId(),
                request.type(),
                request.channel(),
                eventId
        );
        String payload = serializePayload(request);

        Optional<NotificationCreateResponse> existingResponse = idempotencyStore.find(dedupKey)
                .flatMap(record -> resolveExistingIdempotencyRecord(dedupKey, record));
        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        User recipient = userRepository.findById(request.recipientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Recipient user not found."));

        notificationTemplateRepository.findByTypeAndChannel(
                        request.type(),
                        request.channel()
                )
                .filter(NotificationTemplate::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Active notification template not found."
                ));

        if (!idempotencyStore.saveProcessingIfAbsent(dedupKey, PROCESSING_TTL)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Same notification request is already processing.");
        }

        Notification savedNotification;
        try {
            Notification notification = Notification.createPending(
                    recipient,
                    request.type(),
                    request.channel(),
                    dedupKey,
                    payload,
                    DEFAULT_MAX_RETRY_COUNT,
                    LocalDateTime.now(clock)
            );
            savedNotification = notificationRepository.saveAndFlush(notification);
            idempotencyStore.saveCompleted(dedupKey, savedNotification.getId(), COMPLETED_TTL);
        } catch (DataIntegrityViolationException ex) {
            idempotencyStore.clear(dedupKey);
            return notificationRepository.findByDedupKey(dedupKey)
                    .map(NotificationCreateResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Duplicate notification request."));
        } catch (RuntimeException ex) {
            idempotencyStore.clear(dedupKey);
            throw ex;
        }

        log.info(
                "Notification create completed. notificationId={}, recipientId={}, dedupKey={}",
                savedNotification.getId(),
                recipient.getId(),
                dedupKey
        );

        return NotificationCreateResponse.from(savedNotification);
    }

    @Transactional(readOnly = true)
    public NotificationStatusResponse getStatus(Long notificationId, Long authenticatedUserId) {
        Notification notification = findNotification(notificationId);
        validateRecipient(notification, authenticatedUserId);

        return NotificationStatusResponse.from(notification);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long authenticatedUserId) {
        Notification notification = findNotification(notificationId);
        validateRecipient(notification, authenticatedUserId);

        notification.markAsRead(LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<NotificationListItemResponse> getUserNotifications(
            Long recipientId,
            Long authenticatedUserId,
            NotificationReadFilter filter
    ) {
        if (!recipientId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Authenticated user cannot access notifications.");
        }

        NotificationReadFilter resolvedFilter = filter == null ? NotificationReadFilter.ALL : filter;
        return findByReadFilter(recipientId, resolvedFilter).stream()
                .map(NotificationListItemResponse::from)
                .toList();
    }

    private Optional<NotificationCreateResponse> resolveExistingIdempotencyRecord(
            String dedupKey,
            IdempotencyRecord record
    ) {
        if (record.status() == IdempotencyStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Same notification request is already processing.");
        }
        if (record.status() == IdempotencyStatus.COMPLETED) {
            Notification notification = notificationRepository.findById(record.notificationId())
                    .orElseGet(() -> notificationRepository.findByDedupKey(dedupKey)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.RESOURCE_NOT_FOUND,
                                    "Completed idempotency record points to missing notification."
                            )));
            return Optional.of(NotificationCreateResponse.from(notification));
        }
        return Optional.empty();
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found."));
    }

    private void validateRecipient(Notification notification, Long authenticatedUserId) {
        Long recipientId = notification.getRecipient().getId();
        if (!recipientId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Authenticated user cannot access notification.");
        }
    }

    private List<Notification> findByReadFilter(Long recipientId, NotificationReadFilter filter) {
        return switch (filter) {
            case ALL -> notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
            case READ -> notificationRepository.findReadByRecipientId(recipientId);
            case UNREAD -> notificationRepository.findUnreadByRecipientId(recipientId);
        };
    }

    private String extractEventId(NotificationCreateRequest request) {
        Object eventId = request.payload().get("eventId");
        if (!(eventId instanceof String value) || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "payload.eventId is required.");
        }
        return value;
    }

    private String serializePayload(NotificationCreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "payload must be serializable.");
        }
    }
}
