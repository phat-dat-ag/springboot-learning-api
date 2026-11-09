package com.japaneselearning.learning.security.jwt.internal;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Attaches a fresh Learning identity token to each request through the User client.
 */
public final class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final InternalJwtTokenService tokens;

    public ServiceTokenInterceptor(InternalJwtTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution
    ) throws IOException {
        request.getHeaders().setBearerAuth(tokens.generateToken().token());

        return execution.execute(request, body);
    }
}
