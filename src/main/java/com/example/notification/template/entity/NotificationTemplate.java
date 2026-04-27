package com.example.notification.template.entity;

import com.example.notification.common.entity.BaseTimeEntity;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification_template", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_template_type_channel", columnNames = {"type", "channel"})
})
public class NotificationTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "title_template", nullable = false, length = 255)
    private String titleTemplate;

    @Lob
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected NotificationTemplate() {
    }

    private NotificationTemplate(
            NotificationType type,
            NotificationChannel channel,
            String titleTemplate,
            String bodyTemplate,
            boolean active
    ) {
        this.type = type;
        this.channel = channel;
        this.titleTemplate = titleTemplate;
        this.bodyTemplate = bodyTemplate;
        this.active = active;
    }

    public static NotificationTemplate create(
            NotificationType type,
            NotificationChannel channel,
            String titleTemplate,
            String bodyTemplate,
            boolean active
    ) {
        return new NotificationTemplate(type, channel, titleTemplate, bodyTemplate, active);
    }
}
