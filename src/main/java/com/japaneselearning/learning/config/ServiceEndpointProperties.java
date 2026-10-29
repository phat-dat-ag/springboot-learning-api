package com.japaneselearning.learning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServiceEndpointProperties {
    private JwksEndpoint jwks;

    public record JwksEndpoint(
            String baseUrl,
            String getPublicKeys
    ) {
    }
}
