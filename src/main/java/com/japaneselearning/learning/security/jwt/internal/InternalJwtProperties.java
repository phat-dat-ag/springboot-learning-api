package com.japaneselearning.learning.security.jwt.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security.internal-jwt")
public record InternalJwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration ttl
) {
    public InternalJwtProperties {
        if (ttl != null && (ttl.isNegative()
                || ttl.isZero()
                || ttl.getNano() != 0
                || ttl.compareTo(Duration.ofMinutes(5)) > 0)
        ) {
            throw new IllegalArgumentException(
                    "Internal JWT TTL must be a whole number of seconds between 1 and 300"
            );
        }
    }
}
