package com.japaneselearning.learning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import com.japaneselearning.learning.security.jwks.CallerMetadataInterceptor;
import com.japaneselearning.learning.security.jwt.internal.InternalJwtTokenService;
import com.japaneselearning.learning.security.jwt.internal.ServiceTokenInterceptor;
import org.jspecify.annotations.Nullable;

@Configuration
@RequiredArgsConstructor
public class ServiceClientConfig {

    private final ServiceEndpointProperties properties;

    @Bean("jwksRestClient")
    public RestClient jwksServiceRestClient(
            RestClient.Builder builder,
            CallerMetadataInterceptor caller
    ) {
        return builder.clone()
                .baseUrl(properties.getJwks().baseUrl())
                .requestInterceptor(caller)
                .build();
    }

    @Bean("userRestClient")
    public RestClient userRestClient(
            RestClient.Builder builder,
            CallerMetadataInterceptor caller,

            @Nullable
            InternalJwtTokenService tokens,

            @Value("${services.user.internal-jwt-enabled:false}")
            boolean internalJwtEnabled
    ) {
        var endpoint = properties.getUser();
        var requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(endpoint.connectTimeout());
        requestFactory.setReadTimeout(endpoint.readTimeout());

        var userBuilder = builder.clone()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(caller);

        if (internalJwtEnabled) {
            if (tokens == null) {
                throw new IllegalStateException(
                        "User Service JWT attachment requires internal JWT signing to be enabled"
                );
            }

            userBuilder.requestInterceptor(new ServiceTokenInterceptor(tokens));
        }

        return userBuilder.build();
    }

    @Bean
    public CallerMetadataInterceptor jwksCallerInterceptor(
            @Value("${spring.application.name}") String service,
            @Value("${app.instance-id}") String instance,
            @Value("${app.version}") String version
    ) {
        return new CallerMetadataInterceptor(service, instance, version);
    }
}
