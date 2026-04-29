package com.example.notification.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.notification.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupKey(String dedupKey);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    @Query("""
            select n
            from Notification n
            where n.recipient.id = :recipientId
              and n.readAt is not null
            order by n.createdAt desc
            """)
    List<Notification> findReadByRecipientId(Long recipientId);

    @Query("""
            select n
            from Notification n
            where n.recipient.id = :recipientId
              and n.readAt is null
            order by n.createdAt desc
            """)
    List<Notification> findUnreadByRecipientId(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = com.example.notification.notification.entity.NotificationStatus.PROCESSING,
                n.processedAt = null
            where n.id = :notificationId
              and n.status = com.example.notification.notification.entity.NotificationStatus.PENDING
            """)
    int markPendingAsProcessing(@Param("notificationId") Long notificationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = com.example.notification.notification.entity.NotificationStatus.PROCESSING,
                n.processedAt = null
            where n.id = :notificationId
              and n.status = com.example.notification.notification.entity.NotificationStatus.RETRY_WAITING
              and n.nextRetryAt <= :now
            """)
    int markRetryWaitingAsProcessing(
            @Param("notificationId") Long notificationId,
            @Param("now") java.time.LocalDateTime now
    );

    @Query("""
            select n.id
            from Notification n
            where n.status = com.example.notification.notification.entity.NotificationStatus.PENDING
            order by n.createdAt asc
            """)
    List<Long> findPendingDispatchTargetIds(Pageable pageable);

    @Query("""
            select n.id
            from Notification n
            where n.status = com.example.notification.notification.entity.NotificationStatus.RETRY_WAITING
              and n.nextRetryAt <= :now
            order by n.nextRetryAt asc, n.createdAt asc
            """)
    List<Long> findRetryWaitingDispatchTargetIds(
            @Param("now") java.time.LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select n.id
            from Notification n
            where n.status = com.example.notification.notification.entity.NotificationStatus.PROCESSING
              and n.updatedAt <= :staleBefore
            order by n.updatedAt asc
            """)
    List<Long> findStaleProcessingTargetIds(
            @Param("staleBefore") java.time.LocalDateTime staleBefore,
            Pageable pageable
    );

    @Query("""
            select n
            from Notification n
            where n.status = com.example.notification.notification.entity.NotificationStatus.FAILED
              and n.retryCount >= n.maxRetryCount
            order by n.processedAt desc, n.createdAt desc
            """)
    List<Notification> findExhaustedFailedNotifications(Pageable pageable);
}
