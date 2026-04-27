package com.example.notification.dispatchhistory.entity;

import java.time.LocalDateTime;

import com.example.notification.common.entity.BaseTimeEntity;
import com.example.notification.notification.entity.Notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification_dispatch_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatch_history_notification_attempt", columnNames = {"notification_id", "attempt"})
})
public class NotificationDispatchHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "dispatched_at", nullable = false)
    private LocalDateTime dispatchedAt;

    protected NotificationDispatchHistory() {
    }

    private NotificationDispatchHistory(
            Notification notification,
            int attempt,
            DispatchStatus status,
            String errorCode,
            String errorMessage,
            LocalDateTime dispatchedAt
    ) {
        this.notification = notification;
        this.attempt = attempt;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.dispatchedAt = dispatchedAt;
    }

    public static NotificationDispatchHistory create(
            Notification notification,
            int attempt,
            DispatchStatus status,
            String errorCode,
            String errorMessage,
            LocalDateTime dispatchedAt
    ) {
        return new NotificationDispatchHistory(notification, attempt, status, errorCode, errorMessage, dispatchedAt);
    }
}
