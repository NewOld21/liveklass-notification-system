package com.example.notification.notification.sender;

import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.template.entity.NotificationTemplate;

public interface NotificationSender {

    boolean supports(NotificationChannel channel);

    NotificationSendResult send(Notification notification, NotificationTemplate template);
}
