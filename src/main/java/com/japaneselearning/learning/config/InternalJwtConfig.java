package com.japaneselearning.learning.config;

import com.japaneselearning.learning.security.InternalJwtTokenService;
import com.japaneselearning.learning.security.InternalSigningKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.security.internal-jwt",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties({
        InternalJwtProperties.class,
        InternalSigningKeyProperties.class
})
public class InternalJwtConfig {
    @Bean
    public InternalSigningKeyProvider internalSigningKeyProvider(
            InternalSigningKeyProperties properties
    ) {
        return new InternalSigningKeyProvider(properties);
    }

    @Bean
    public InternalJwtTokenService internalJwtTokenService(
            InternalJwtProperties properties,
            InternalSigningKeyProvider keys
    ) {
        return new InternalJwtTokenService(properties, keys);
    }
}
