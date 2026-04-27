package com.example.notification.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserSignupRequest(
        @Email(message = "Invalid email format.")
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be 100 characters or less.")
        String name,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters.")
        String password
) {
}
