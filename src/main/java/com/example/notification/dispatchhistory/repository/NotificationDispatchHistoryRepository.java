package com.example.notification.dispatchhistory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;

public interface NotificationDispatchHistoryRepository extends JpaRepository<NotificationDispatchHistory, Long> {

    List<NotificationDispatchHistory> findByNotificationIdOrderByAttemptAsc(Long notificationId);
}
