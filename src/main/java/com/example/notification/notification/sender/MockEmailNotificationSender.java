package com.example.notification.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.template.entity.NotificationTemplate;

@Component
public class MockEmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationSender.class);

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }

    @Override
    public NotificationSendResult send(Notification notification, NotificationTemplate template) {
        log.info(
                "Mock email notification sent. notificationId={}, recipientId={}, type={}, title={}",
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getType(),
                template.getTitleTemplate()
        );
        return NotificationSendResult.success();
    }
}
