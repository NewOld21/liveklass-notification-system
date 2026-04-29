package com.example.notification.notification.entity;

import java.time.LocalDateTime;

import com.example.notification.common.entity.BaseTimeEntity;
import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.notification.service.NotificationProcessingPolicy;
import com.example.notification.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_dedup_key", columnNames = "dedup_key")
}, indexes = {
        @Index(name = "idx_notification_status_next_retry_at", columnList = "status,next_retry_at"),
        @Index(name = "idx_notification_recipient_created_at", columnList = "recipient_id,created_at")
})
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status;

    @Column(name = "dedup_key", nullable = false, length = 255)
    private String dedupKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private int maxRetryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    private Notification(
            User recipient,
            NotificationType type,
            NotificationChannel channel,
            NotificationStatus status,
            String dedupKey,
            String payload,
            int retryCount,
            int maxRetryCount,
            LocalDateTime requestedAt
    ) {
        this.recipient = recipient;
        this.type = type;
        this.channel = channel;
        this.status = status;
        this.dedupKey = dedupKey;
        this.payload = payload;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
        this.requestedAt = requestedAt;
    }

    public static Notification createPending(
            User recipient,
            NotificationType type,
            NotificationChannel channel,
            String dedupKey,
            String payload,
            int maxRetryCount,
            LocalDateTime requestedAt
    ) {
        return new Notification(
                recipient,
                type,
                channel,
                NotificationStatus.PENDING,
                dedupKey,
                payload,
                0,
                maxRetryCount,
                requestedAt
        );
    }

    public void markAsRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    public boolean isProcessableAt(LocalDateTime now) {
        if (status == NotificationStatus.PENDING) {
            return true;
        }
        return status == NotificationStatus.RETRY_WAITING
                && nextRetryAt != null
                && !nextRetryAt.isAfter(now);
    }

    public void startProcessing(LocalDateTime now) {
        if (!isProcessableAt(now)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification cannot start processing.");
        }

        this.status = NotificationStatus.PROCESSING;
        this.processedAt = null;
    }

    public void markSent(LocalDateTime processedAt) {
        validateProcessingState();

        this.status = NotificationStatus.SENT;
        this.nextRetryAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.processedAt = processedAt;
    }

    public void markDispatchFailed(String errorCode, String errorMessage, LocalDateTime failedAt) {
        validateProcessingState();

        int nextRetryCount = retryCount + 1;
        this.retryCount = nextRetryCount;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.processedAt = failedAt;

        if (nextRetryCount <= maxRetryCount) {
            this.status = NotificationStatus.RETRY_WAITING;
            this.nextRetryAt = failedAt.plus(NotificationProcessingPolicy.retryBackoff(nextRetryCount));
            return;
        }

        this.status = NotificationStatus.FAILED;
        this.nextRetryAt = null;
    }

    public void reopenForManualRetry(LocalDateTime nextRetryAt) {
        if (status != NotificationStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only failed notification can be reopened.");
        }

        this.status = NotificationStatus.RETRY_WAITING;
        this.retryCount = 0;
        this.nextRetryAt = nextRetryAt;
        this.processedAt = null;
    }

    private void validateProcessingState() {
        if (status != NotificationStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification is not processing.");
        }
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getPayload() {
        return payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
