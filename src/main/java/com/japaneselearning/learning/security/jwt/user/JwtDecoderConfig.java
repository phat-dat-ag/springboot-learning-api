package com.japaneselearning.learning.security.jwt.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;

import java.time.Instant;

import com.japaneselearning.learning.security.jwks.CallerMetadataInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.restclient.RestTemplateBuilder;

/**
 * Verifies incoming user access tokens using User's public keys from JWKS.
 */
@Configuration
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtDecoderConfig {
    @Bean
    public JwtDecoder jwtDecoder(JwtSecurityProperties properties, CallerMetadataInterceptor caller, RestTemplateBuilder builder) {
        RestTemplate jwksHttp = builder.additionalInterceptors(caller).build();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .restOperations(jwksHttp)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                jwt -> {
                    // Require the fields User Service always emits; timestamp validation
                    // alone allows an absent expiration.
                    Object kid = jwt.getHeaders().get("kid");

                    if (!(kid instanceof String id)
                            || id.isBlank()
                            || jwt.getSubject() == null
                            || jwt.getSubject().isBlank()
                            || jwt.getExpiresAt() == null
                            || jwt.getIssuedAt() == null
                            || jwt.getIssuedAt().isAfter(Instant.now().plus(properties.clockSkew()))
                            || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    ) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(
                                        "invalid_token",
                                        "Invalid access token claims",
                                        null
                                )
                        );
                    }
                    return OAuth2TokenValidatorResult.success();
                }
        ));

        return decoder;
    }
}
