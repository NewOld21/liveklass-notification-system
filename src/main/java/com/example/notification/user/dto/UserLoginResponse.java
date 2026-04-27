package com.example.notification.user.dto;

public record UserLoginResponse(
        UserResponse user,
        String tokenType,
        String accessToken,
        long expiresIn
) {

    public static UserLoginResponse bearer(UserResponse user, String accessToken, long expiresIn) {
        return new UserLoginResponse(user, "Bearer", accessToken, expiresIn);
    }
}
