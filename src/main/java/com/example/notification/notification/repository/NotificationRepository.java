package com.example.notification.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
