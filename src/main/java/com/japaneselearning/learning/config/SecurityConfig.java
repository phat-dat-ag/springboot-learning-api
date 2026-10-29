package com.japaneselearning.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import com.japaneselearning.learning.security.JwtRoleConverter;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtRoleConverter roles) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(roles);

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                                                     @Override
                                                     public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                                         var failureHandler = new AuthenticationEntryPointFailureHandler(
                                                                 new BearerTokenAuthenticationEntryPoint()
                                                         );

                                                         // JWKS retrieval failures must also fail authentication with 401.
                                                         failureHandler.setRethrowAuthenticationServiceException(false);
                                                         filter.setAuthenticationFailureHandler(failureHandler);

                                                         return filter;
                                                     }
                                                 }
                        )
                );

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        HttpMethod.GET,
                        "/test",
                        "/actuator/health",
                        "/actuator/health/**"
                ).permitAll()

                .requestMatchers(
                        HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                ).permitAll()

                .requestMatchers(
                        "/api/v1/integration/**"
                ).permitAll()

                .requestMatchers(
                        "/api/v1/learning/**"
                ).hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated());
        return http.build();
    }
}
