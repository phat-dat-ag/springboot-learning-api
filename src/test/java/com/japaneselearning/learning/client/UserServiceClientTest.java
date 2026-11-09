package com.japaneselearning.learning.client;

import com.japaneselearning.learning.security.jwks.CallerMetadataInterceptor;
import com.japaneselearning.learning.common.exception.BusinessException;
import com.japaneselearning.learning.common.exception.ErrorCode;
import com.japaneselearning.learning.config.ServiceClientConfig;
import com.japaneselearning.learning.config.ServiceEndpointProperties;
import com.japaneselearning.learning.security.jwt.internal.InternalJwtProperties;
import com.japaneselearning.learning.security.jwt.internal.InternalSigningKeyProperties;
import com.japaneselearning.learning.security.jwt.internal.InternalJwtTokenService;
import com.japaneselearning.learning.security.jwt.internal.InternalSigningKeyProvider;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import java.util.List;
import java.util.Base64;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    @TempDir
    Path directory;

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
        return client(readTimeout, null, false);
    }

    private UserServiceClient client(Duration readTimeout, InternalJwtTokenService tokens, boolean attach) {
        var properties = new ServiceEndpointProperties();
        properties.setUser(new ServiceEndpointProperties.UserEndpoint(
                "http://127.0.0.1:" + server.getAddress().getPort(), "/internal/users",
                Duration.ofSeconds(1), readTimeout));
        var builder = RestClient.builder().defaultHeader("X-Custom", "retained");
        if (attach) {
            builder.defaultHeader("Authorization", "Bearer user-token-must-not-be-forwarded");
        }
        var restClient = new ServiceClientConfig(properties).userRestClient(builder,
                new CallerMetadataInterceptor("learning-api", "test-instance", "1.0"), tokens, attach);
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

    @Test
    void attachesFreshSignedServiceTokenForEveryRequest(CapturedOutput output) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path path = directory.resolve("learning.pem");
        Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        var key = new InternalSigningKeyProperties.Key();
        key.setKid("learning-test-v1");
        key.setPrivateKeyPath(path.toString());
        key.setActive(true);
        var keys = new InternalSigningKeyProperties();
        keys.setKeys(List.of(key));
        var tokens = new InternalJwtTokenService(
                new InternalJwtProperties("learning-service", "user-service", Duration.ofSeconds(60)),
                new InternalSigningKeyProvider(keys));
        client = client(Duration.ofSeconds(2), tokens, true);
        MDC.put("correlationId", "request-123");

        client.getUser(ID);
        assertThat(authorization.get().startsWith("Bearer ")).isTrue();
        var first = SignedJWT.parse(authorization.get().substring(7));
        assertThat(first.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
        assertThat(first.getJWTClaimsSet().getIssuer()).isEqualTo("learning-service");
        assertThat(first.getJWTClaimsSet().getAudience()).containsExactly("user-service");
        assertThat(first.getHeader().getKeyID()).isEqualTo("learning-test-v1");
        assertThat(correlation.get()).isEqualTo("request-123");
        assertThat(caller.get()).isEqualTo("learning-api");
        assertThat(customHeader.get()).isEqualTo("retained");
        assertThat(output.getAll().contains(authorization.get().substring(7))).isFalse();

        client.getUser(ID);
        var second = SignedJWT.parse(authorization.get().substring(7));
        assertThat(second.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
        assertThat(second.getJWTClaimsSet().getJWTID()).isNotEqualTo(first.getJWTClaimsSet().getJWTID());
        assertThat(output.getAll().contains(authorization.get().substring(7))).isFalse();
    }

    @Test
    void attachmentCannotBeEnabledWithoutSigner() {
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User Service JWT attachment requires internal JWT signing to be enabled");
    }

    @Test
    void signingFailureDoesNotSendAnonymousRequest() {
        var tokens = mock(InternalJwtTokenService.class);
        when(tokens.generateToken()).thenThrow(new IllegalStateException("Signing unavailable"));
        client = client(Duration.ofSeconds(2), tokens, true);
        assertThatThrownBy(() -> client.getUser(ID)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Signing unavailable");
        assertThat(path.get()).isNull();
    }

    @Test
    void disabledAttachmentDoesNotGenerateTokens() {
        var tokens = mock(InternalJwtTokenService.class);
        client = client(Duration.ofSeconds(2), tokens, false);
        client.getUser(ID);
        assertThat(authorization.get()).isNull();
        verifyNoInteractions(tokens);
    }
}
