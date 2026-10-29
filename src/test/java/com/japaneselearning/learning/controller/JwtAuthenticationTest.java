package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.config.JwtDecoderConfig;
import com.japaneselearning.learning.config.JwtSecurityProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@AutoConfigureMockMvc
class JwtAuthenticationTest {
    private static final String ISSUER = "japaneselearning-user-service";
    private static final RSAKey V1 = key("test-v1");
    private static final RSAKey V2 = key("test-v2");
    private static final RSAKey UNKNOWN = key("unknown");
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static volatile String jwks = publicKeys(V1, V2);
    private static volatile boolean unavailable;
    private static final HttpServer SERVER = server();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt.jwk-set-uri", JwtAuthenticationTest::uri);
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @AfterEach
    void reset() {
        unavailable = false;
        jwks = publicKeys(V1, V2);
    }

    @Test
    void missingToken() throws Exception {
        mvc.perform(get("/api/v1/learning/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken() throws Exception {
        request("not.a.jwt", 401);
    }

    @Test
    void userTokenWithV1() throws Exception {
        mvc.perform(get("/api/v1/learning/me").header("Authorization", "Bearer " + token(V1, claims())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("42"))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.issuer").value(ISSUER))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void adminTokenWithV2() throws Exception {
        mvc.perform(get("/api/v1/learning/me").header("Authorization", "Bearer " +
                        token(V2, claims().claim("role", 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authorities[0]").value("ROLE_ADMIN"));
    }

    @Test
    void expired() throws Exception {
        request(token(V1, claims().issueTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120)))), 401);
    }

    @Test
    void invalidSignatureForTrustedKid() throws Exception {
        request(sign(UNKNOWN, V1.getKeyID(), JWSAlgorithm.RS256, claims()), 401);
    }

    @Test
    void unknownKey() throws Exception {
        request(token(UNKNOWN, claims()), 401);
    }

    @Test
    void untrustedIssuer() throws Exception {
        request(token(V1, claims().issuer("untrusted")), 401);
    }

    @Test
    void insufficientPermissions() throws Exception {
        request(token(V1, claims().claim("role", 3)), 403);
    }

    @Test
    void missingRole() throws Exception {
        request(token(V1, claims().claim("role", null)), 403);
    }

    @Test
    void stringRoleMatchesIssuerSemantics() throws Exception {
        request(token(V1, claims().claim("role", "1")), 200);
    }

    @Test
    void tamperedPayload() throws Exception {
        String valid = token(V1, claims());
        String[] parts = valid.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("42", "43");
        request(parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2], 401);
    }

    @Test
    void futureNotBefore() throws Exception {
        request(token(V1, claims().notBeforeTime(Date.from(Instant.now().plusSeconds(120)))), 401);
    }

    @Test
    void futureIssuedAt() throws Exception {
        request(token(V1, claims().issueTime(Date.from(Instant.now().plusSeconds(120)))), 401);
    }

    @Test
    void missingExpiration() throws Exception {
        request(token(V1, claims().expirationTime(null)), 401);
    }

    @Test
    void missingSubject() throws Exception {
        request(token(V1, claims().subject(null)), 401);
    }

    @Test
    void missingKid() throws Exception {
        request(sign(V1, null, JWSAlgorithm.RS256, claims()), 401);
    }

    @Test
    void wrongAlgorithm() throws Exception {
        request(sign(V1, V1.getKeyID(), JWSAlgorithm.RS512, claims()), 401);
    }

    @Test
    void clockSkewAllowsRecentlyExpiredToken() throws Exception {
        request(token(V1, claims().issueTime(Date.from(Instant.now().minusSeconds(90)))
                .expirationTime(Date.from(Instant.now().minusSeconds(10)))), 200);
    }

    @Test
    void rotationRefreshesNewKidAndRetainsOldVerificationKey() throws Exception {
        jwks = publicKeys(V1);
        var decoder = decoder();
        decoder.decode(token(V1, claims()));
        int before = REQUESTS.get();
        decoder.decode(token(V1, claims()));
        assertThat(REQUESTS.get()).isEqualTo(before);
        jwks = publicKeys(V1, V2);
        decoder.decode(token(V2, claims()));
        assertThat(REQUESTS.get()).isGreaterThan(before);
        decoder.decode(token(V1, claims()));
    }

    @Test
    void outageWithoutCacheFailsClosed() throws Exception {
        unavailable = true;
        var decoder = decoder();
        String token = token(V1, claims());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        request(token(UNKNOWN, claims()), 401);
    }

    @Test
    void outageCanUseCachedKeyButCannotAcceptUnknownKey() throws Exception {
        var decoder = decoder();
        String valid = token(V1, claims());
        decoder.decode(valid);
        unavailable = true;
        assertThat(decoder.decode(valid).getSubject()).isEqualTo("42");
        String unknown = token(UNKNOWN, claims());
        assertThatThrownBy(() -> decoder.decode(unknown)).isInstanceOf(JwtException.class);
    }

    private org.springframework.security.oauth2.jwt.JwtDecoder decoder() {
        return new JwtDecoderConfig().jwtDecoder(new JwtSecurityProperties(uri(), ISSUER, Duration.ofSeconds(60)));
    }

    private void request(String token, int status) throws Exception {
        mvc.perform(get("/api/v1/learning/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is(status));
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject("42").issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString()).claim("role", 1);
    }

    private static String token(RSAKey key, JWTClaimsSet.Builder claims) throws Exception {
        return sign(key, key.getKeyID(), JWSAlgorithm.RS256, claims);
    }

    private static String sign(RSAKey key, String kid, JWSAlgorithm algorithm, JWTClaimsSet.Builder claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).keyID(kid).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey key(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String publicKeys(RSAKey... keys) {
        return new JWKSet(Arrays.stream(keys).map(k -> (JWK) k.toPublicJWK()).toList()).toString();
    }

    private static String uri() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks";
    }

    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                REQUESTS.incrementAndGet();
                byte[] body = (unavailable ? "unavailable" : jwks).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(unavailable ? 503 : 200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
