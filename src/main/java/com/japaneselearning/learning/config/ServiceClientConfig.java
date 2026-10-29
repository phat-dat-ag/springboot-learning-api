package com.japaneselearning.learning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class ServiceClientConfig {

    private final ServiceEndpointProperties properties;

    @Bean("jwksRestClient")
    public RestClient jwksServiceRestClient() {
        return RestClient.builder()
                .baseUrl(properties.getJwks().baseUrl())
                .build();
    }
}