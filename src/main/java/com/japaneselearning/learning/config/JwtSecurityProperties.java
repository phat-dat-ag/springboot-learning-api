package com.japaneselearning.learning.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtSecurityProperties(
        @NotBlank String jwkSetUri,
        @NotBlank String issuer,
        @NotNull Duration clockSkew
) {
    public JwtSecurityProperties {
        if (clockSkew != null && clockSkew.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT clock skew must not be negative"
            );
        }
    }
}
