package com.japaneselearning.learning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import com.japaneselearning.learning.client.JwksCallerInterceptor;

@Configuration
@RequiredArgsConstructor
public class ServiceClientConfig {

    private final ServiceEndpointProperties properties;

    @Bean("jwksRestClient")
    public RestClient jwksServiceRestClient(RestClient.Builder builder, JwksCallerInterceptor caller) {
        return builder.clone()
                .baseUrl(properties.getJwks().baseUrl())
                .requestInterceptor(caller)
                .build();
    }

    @Bean
    public JwksCallerInterceptor jwksCallerInterceptor(
            @Value("${spring.application.name}") String service,
            @Value("${app.instance-id}") String instance,
            @Value("${app.version}") String version) {
        return new JwksCallerInterceptor(service, instance, version);
    }
}
