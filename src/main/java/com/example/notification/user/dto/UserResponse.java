package com.example.notification.user.dto;

import com.example.notification.user.entity.User;
import com.example.notification.user.entity.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String name,
        UserStatus status
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getStatus());
    }
}
