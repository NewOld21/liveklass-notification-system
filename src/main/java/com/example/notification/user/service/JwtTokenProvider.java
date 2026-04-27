package com.example.notification.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.notification.user.entity.User;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Clock clock;
    private final String secret;
    private final long expirationSeconds;

    public JwtTokenProvider(
            Clock clock,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.clock = clock;
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        String header = encode("""
                {"alg":"HS256","typ":"JWT"}
                """.strip());
        String payload = encode("""
                {"sub":"%d","email":"%s","name":"%s","iat":%d,"exp":%d}
                """.formatted(
                user.getId(),
                escapeJson(user.getEmail()),
                escapeJson(user.getName()),
                now.getEpochSecond(),
                expiresAt.getEpochSecond()
        ).strip());

        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private String encode(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create JWT signature.", ex);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
