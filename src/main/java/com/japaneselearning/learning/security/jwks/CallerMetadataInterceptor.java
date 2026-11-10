package com.japaneselearning.learning.security.jwks;

import com.japaneselearning.learning.logging.filter.RequestLoggingFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds diagnostic caller metadata to outbound User and JWKS requests.
 * These self-reported headers are not authentication credentials.
 * Tracing headers managed by instrumentation are left unchanged.
 */
public record CallerMetadataInterceptor(
        String service, String instance, String version
) implements ClientHttpRequestInterceptor {

    public CallerMetadataInterceptor {
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
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        var headers = request.getHeaders();

        headers.set("X-Caller-Service", service);
        headers.set("X-Caller-Instance", instance);
        headers.set("X-Caller-Version", version);

        String correlationId = MDC.get(RequestLoggingFilter.CORRELATION_ID);

        headers.set(
                RequestLoggingFilter.HEADER_NAME,
                valid(correlationId) ? correlationId : UUID.randomUUID().toString()
        );

        return execution.execute(request, body);
    }
}
