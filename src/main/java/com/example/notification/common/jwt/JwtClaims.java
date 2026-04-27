package com.example.notification.common.jwt;

public record JwtClaims(
        Long userId,
        String email,
        String name,
        long issuedAt,
        long expiresAt
) {
}
