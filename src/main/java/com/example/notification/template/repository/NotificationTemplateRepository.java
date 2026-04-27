package com.example.notification.template.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.template.entity.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTypeAndChannel(NotificationType type, NotificationChannel channel);
}
