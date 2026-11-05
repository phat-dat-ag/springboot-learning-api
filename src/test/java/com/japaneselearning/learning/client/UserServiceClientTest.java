package com.japaneselearning.learning.client;

import com.japaneselearning.learning.common.exception.BusinessException;
import com.japaneselearning.learning.common.exception.ErrorCode;
import com.japaneselearning.learning.config.ServiceClientConfig;
import com.japaneselearning.learning.config.ServiceEndpointProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class UserServiceClientTest {
    private static final UUID ID = UUID.fromString("f05bf709-5cb8-4a4c-9ac4-e537b5579aef");
    private HttpServer server;
    private UserServiceClient client;
    private volatile int status = 200;
    private volatile String body;
    private volatile long delayMillis;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> correlation = new AtomicReference<>();
    private final AtomicReference<String> caller = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> customHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/users", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            caller.set(exchange.getRequestHeaders().getFirst("X-Caller-Service"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            customHeader.set(exchange.getRequestHeaders().getFirst("X-Custom"));
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        body = """
                {"user":{"id":"%s","username":"test-user","email":"test@example.invalid",
                "isActivated":true,"createdAt":"2026-11-01T10:00:00","updatedAt":null}}
                """.formatted(ID);
        client = client(Duration.ofSeconds(2));
    }

    private UserServiceClient client(Duration readTimeout) {
        var properties = new ServiceEndpointProperties();
        properties.setUser(new ServiceEndpointProperties.UserEndpoint(
                "http://127.0.0.1:" + server.getAddress().getPort(), "/internal/users",
                Duration.ofSeconds(1), readTimeout));
        var builder = RestClient.builder().defaultHeader("X-Custom", "retained");
        var restClient = new ServiceClientConfig(properties).userRestClient(builder,
                new JwksCallerInterceptor("learning-api", "test-instance", "1.0"));
        return new UserServiceClient(restClient, properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        MDC.remove("correlationId");
    }

    @Test
    void readsCurrentUserContractAndSendsMetadataWithoutAuthentication() {
        MDC.put("correlationId", "request-123");
        var response = client.getUser(ID);
        assertThat(response.user().id()).isEqualTo(ID);
        assertThat(response.user().username()).isEqualTo("test-user");
        assertThat(response.user().email()).isEqualTo("test@example.invalid");
        assertThat(response.user().isActivated()).isTrue();
        assertThat(response.user().createdAt()).isEqualTo("2026-11-01T10:00:00");
        assertThat(response.user().updatedAt()).isNull();
        assertThat(path.get()).isEqualTo("/internal/users/" + ID);
        assertThat(correlation.get()).isEqualTo("request-123");
        assertThat(caller.get()).isEqualTo("learning-api");
        assertThat(customHeader.get()).isEqualTo("retained");
        assertThat(authorization.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
    void upstreamErrorsKeepExistingErrorContractWithoutLoggingBodies(int upstreamStatus, CapturedOutput output) {
        status = upstreamStatus;
        body = "{\"message\":\"private-upstream-details\"}";
        assertFailure();
        assertThat(output.getAll()).doesNotContain("private-upstream-details");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "null", "{\"user\":null}", "{\"user\":{}}", "invalid-json",
            "{\"data\":{\"user\":{}}}",
            "{\"user\":{\"id\":\"00000000-0000-0000-0000-000000000000\"}}"})
    void rejectsMalformedOrIncompleteSuccessfulResponses(String upstreamBody) {
        body = upstreamBody;
        assertFailure();
    }

    @Test
    void enforcesConfiguredReadTimeout() {
        delayMillis = 1000;
        client = client(Duration.ofMillis(100));
        long started = System.nanoTime();
        assertFailure();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(900));
    }

    @Test
    void handlesUnavailableUserService() {
        server.stop(0);
        assertFailure();
    }

    @Test
    void rejectsTimeoutsThatWouldDisableOrOverflowClientLimits() {
        for (Duration timeout : new Duration[]{Duration.ZERO, Duration.ofMillis(-1),
                Duration.ofNanos(1), Duration.ofDays(30)}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ServiceEndpointProperties.UserEndpoint("http://user", "/internal/users",
                            timeout, Duration.ofSeconds(1)));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ServiceEndpointProperties.UserEndpoint("http://user", "/internal/users",
                            Duration.ofSeconds(1), timeout));
        }
    }

    private void assertFailure() {
        assertThatThrownBy(() -> client.getUser(ID)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
