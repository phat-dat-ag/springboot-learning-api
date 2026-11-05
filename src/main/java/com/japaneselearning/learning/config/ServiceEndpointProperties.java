package com.japaneselearning.learning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServiceEndpointProperties {
    private JwksEndpoint jwks;
    private UserEndpoint user;

    public record JwksEndpoint(
            String baseUrl,
            String getPublicKeys
    ) {
    }

    public record UserEndpoint(
            String baseUrl,
            String getUser,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        public UserEndpoint {
            if (baseUrl == null
                    || baseUrl.isBlank()
                    || getUser == null
                    || !getUser.startsWith("/")
            ) {
                throw new IllegalArgumentException(
                        "User Service base URL and endpoint path are required"
                );
            }
            // Sub-millisecond values become zero (an infinite timeout) in the HTTP client.
            if (connectTimeout == null
                    || connectTimeout.toMillis() < 1
                    || connectTimeout.toMillis() > Integer.MAX_VALUE
                    || readTimeout == null
                    || readTimeout.toMillis() < 1
                    || readTimeout.toMillis() > Integer.MAX_VALUE
            ) {
                throw new IllegalArgumentException(
                        "User Service timeouts must be between 1 and 2147483647 ms"
                );
            }
        }
    }
}
