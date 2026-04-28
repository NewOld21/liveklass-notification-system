package com.example.notification.notification.sender;

public record NotificationSendResult(
        boolean successful,
        String errorCode,
        String errorMessage
) {

    public static NotificationSendResult success() {
        return new NotificationSendResult(true, null, null);
    }

    public static NotificationSendResult failure(String errorCode, String errorMessage) {
        return new NotificationSendResult(false, errorCode, errorMessage);
    }
}
