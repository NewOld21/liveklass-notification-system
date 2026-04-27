package com.example.notification.common.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.user.entity.User;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

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

    public JwtClaims validateAccessToken(String token) {
        String[] parts = splitToken(token);
        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);

        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String payload = decode(parts[1]);
        long expiresAt = extractLong(payload, "exp");
        if (expiresAt <= Instant.now(clock).getEpochSecond()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        return new JwtClaims(
                extractLong(payload, "sub"),
                extractString(payload, "email"),
                extractString(payload, "name"),
                extractLong(payload, "iat"),
                expiresAt
        );
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private String[] splitToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return parts;
    }

    private String encode(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(BASE64_URL_DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
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

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);

        if (expectedBytes.length != actualBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        start += pattern.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                value.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }

        throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }

    private long extractLong(String json, String key) {
        String stringValue = extractRawValue(json, key);
        try {
            return Long.parseLong(stringValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String extractRawValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        start += pattern.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }

        return json.substring(start, end).replace("\"", "").trim();
    }
}
