package com.example.notification.dispatchhistory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;

public interface NotificationDispatchHistoryRepository extends JpaRepository<NotificationDispatchHistory, Long> {
}
