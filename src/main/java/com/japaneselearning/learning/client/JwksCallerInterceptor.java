package com.japaneselearning.learning.client;

import com.japaneselearning.learning.common.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds caller metadata without changing tracing headers managed by instrumentation.
 */
public record JwksCallerInterceptor(String service, String instance, String version)
        implements ClientHttpRequestInterceptor {
    public JwksCallerInterceptor {
        service = identity(service);
        instance = identity(instance);
        version = identity(version);
    }

    private static String identity(String value) {
        return valid(value) ? value : "unknown";
    }

    private static boolean valid(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        var headers = request.getHeaders();
        headers.set("X-Caller-Service", service);
        headers.set("X-Caller-Instance", instance);
        headers.set("X-Caller-Version", version);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID);
        headers.set(CorrelationIdFilter.HEADER_NAME,
                valid(correlationId) ? correlationId : UUID.randomUUID().toString());
        return execution.execute(request, body);
    }
}
