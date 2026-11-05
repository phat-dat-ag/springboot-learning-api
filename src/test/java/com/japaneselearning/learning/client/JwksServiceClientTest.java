package com.japaneselearning.learning.client;

import com.japaneselearning.learning.config.ServiceClientConfig;
import com.japaneselearning.learning.config.ServiceEndpointProperties;
import com.japaneselearning.learning.security.InternalJwtTokenService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.*;

class JwksServiceClientTest {
    @Test
    void configuredClientSendsMetadataAndRetainsBuilderCustomizations() {
        var properties = new ServiceEndpointProperties();
        properties.setJwks(new ServiceEndpointProperties.JwksEndpoint("http://jwks", "/keys"));
        String trace = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            request.getHeaders().set("traceparent", trace);
            return execution.execute(request, body);
        });
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new ServiceClientConfig(properties).jwksServiceRestClient(builder,
                new JwksCallerInterceptor("learning-api", "pod-7", "1.0"));
        server.expect(requestTo("http://jwks/keys"))
                .andExpect(header("X-Caller-Service", "learning-api"))
                .andExpect(header("X-Caller-Instance", "pod-7"))
                .andExpect(header("X-Caller-Version", "1.0"))
                .andExpect(header("X-Correlation-ID", "request-123"))
                .andExpect(header("traceparent", trace))
                .andRespond(withSuccess("{\"keys\":[]}", MediaType.APPLICATION_JSON));
        MDC.put("correlationId", "request-123");
        try {
            new JwksServiceClient(client, properties).getPublicKeys();
            server.verify();
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    void userAuthenticationDoesNotLeakIntoJwksClient() {
        var properties = new ServiceEndpointProperties();
        properties.setJwks(new ServiceEndpointProperties.JwksEndpoint("http://jwks", "/keys"));
        properties.setUser(new ServiceEndpointProperties.UserEndpoint("http://user", "/internal/users",
                Duration.ofSeconds(1), Duration.ofSeconds(2)));
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var config = new ServiceClientConfig(properties);
        var caller = new JwksCallerInterceptor("learning-api", "test-instance", "1.0");
        var tokens = mock(InternalJwtTokenService.class);
        config.userRestClient(builder, caller, tokens, true);
        var jwks = config.jwksServiceRestClient(builder, caller);
        server.expect(requestTo("http://jwks/keys"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"keys\":[]}", MediaType.APPLICATION_JSON));
        new JwksServiceClient(jwks, properties).getPublicKeys();
        server.verify();
        verifyNoInteractions(tokens);
    }
}
