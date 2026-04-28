package com.example.notification.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.template.entity.NotificationTemplate;

@Component
public class MockInAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockInAppNotificationSender.class);

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP;
    }

    @Override
    public NotificationSendResult send(Notification notification, NotificationTemplate template) {
        log.info(
                "Mock in-app notification sent. notificationId={}, recipientId={}, type={}, title={}",
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getType(),
                template.getTitleTemplate()
        );
        return NotificationSendResult.success();
    }
}
