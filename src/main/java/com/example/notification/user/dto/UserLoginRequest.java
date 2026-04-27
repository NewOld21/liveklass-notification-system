package com.example.notification.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @Email(message = "Invalid email format.")
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Password is required.")
        String password
) {
}
