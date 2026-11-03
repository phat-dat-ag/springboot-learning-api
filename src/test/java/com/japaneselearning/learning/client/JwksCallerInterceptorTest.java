package com.japaneselearning.learning.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwksCallerInterceptorTest {
    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void propagatesCurrentCorrelationAndPreservesTraceHeaders() throws Exception {
        MDC.put("correlationId", "request-123");
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/jwks"));
        String trace = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        request.getHeaders().set("traceparent", trace);
        request.getHeaders().set("tracestate", "vendor=value");
        request.getHeaders().add("X-Caller-Service", "spoofed");
        new JwksCallerInterceptor("learning-api", "pod-7", "1.2.3").intercept(request, new byte[0], (out, body) -> {
            assertThat(out.getHeaders().get("X-Caller-Service")).containsExactly("learning-api");
            assertThat(out.getHeaders().getFirst("X-Caller-Instance")).isEqualTo("pod-7");
            assertThat(out.getHeaders().getFirst("X-Caller-Version")).isEqualTo("1.2.3");
            assertThat(out.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("request-123");
            assertThat(out.getHeaders().getFirst("traceparent")).isEqualTo(trace);
            assertThat(out.getHeaders().getFirst("tracestate")).isEqualTo("vendor=value");
            return new MockClientHttpResponse(new byte[0], 200);
        });
        assertThat(MDC.get("correlationId")).isEqualTo("request-123");
    }

    @Test
    void generatesValidCorrelationWithoutLeakingContext() throws Exception {
        var interceptor = new JwksCallerInterceptor("learning-api", "pod-7", "1.0");
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 2; i++) {
            var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/jwks"));
            interceptor.intercept(request, new byte[0], (out, body) -> {
                String id = out.getHeaders().getFirst("X-Correlation-ID");
                assertThat(UUID.fromString(id).toString()).isEqualTo(id);
                ids.add(id);
                return new MockClientHttpResponse(new byte[0], 200);
            });
        }
        assertThat(ids).hasSize(2);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void replacesInvalidMetadataWithContractCompatibleValues() throws Exception {
        MDC.put("correlationId", "invalid id");
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/jwks"));
        new JwksCallerInterceptor("bad name", "x".repeat(65), "").intercept(request, new byte[0], (out, body) -> {
            for (String header : new String[]{"X-Caller-Service", "X-Caller-Instance", "X-Caller-Version"}) {
                assertThat(out.getHeaders().getFirst(header)).isEqualTo("unknown");
            }
            assertThat(UUID.fromString(out.getHeaders().getFirst("X-Correlation-ID"))).isNotNull();
            return new MockClientHttpResponse(new byte[0], 200);
        });
        assertThat(MDC.get("correlationId")).isEqualTo("invalid id");
    }
}
