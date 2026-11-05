package com.japaneselearning.learning.security;

import com.japaneselearning.learning.config.InternalJwtConfig;
import com.japaneselearning.learning.config.InternalJwtProperties;
import com.japaneselearning.learning.config.InternalSigningKeyProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InternalJwtTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    @TempDir
    static Path directory;
    private static KeyPair pair;
    private static Path privatePath;

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        privatePath = writePem(pair.getPrivate().getEncoded(), "PRIVATE KEY");
    }

    private static Path writePem(byte[] bytes, String label) throws Exception {
        Path path = directory.resolve(UUID.randomUUID() + ".pem");
        Files.writeString(path, "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + label + "-----\n");
        return path;
    }

    private InternalJwtProperties properties() {
        return new InternalJwtProperties("learning-service", "user-service", Duration.ofSeconds(60));
    }

    private InternalSigningKeyProperties.Key key(String kid, Path path, boolean active) {
        var key = new InternalSigningKeyProperties.Key();
        key.setKid(kid);
        key.setPrivateKeyPath(path.toString());
        key.setActive(active);
        return key;
    }

    private InternalSigningKeyProperties keys(InternalSigningKeyProperties.Key... keys) {
        var properties = new InternalSigningKeyProperties();
        properties.setKeys(List.of(keys));
        return properties;
    }

    private InternalSigningKeyProvider provider(Path path) {
        return new InternalSigningKeyProvider(keys(key("learning-rsa-v1", path, true)));
    }

    private InternalJwtTokenService service() {
        return new InternalJwtTokenService(properties(), provider(privatePath),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void signsMinimalServiceTokenWithDedicatedKey() throws Exception {
        var generated = service().generateToken();
        var jwt = SignedJWT.parse(generated.token());
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getType().toString()).isEqualTo("internal+jwt");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("learning-rsa-v1");
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("learning-service");
        assertThat(claims.getSubject()).isEqualTo("learning-service");
        assertThat(claims.getAudience()).containsExactly("user-service");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(60));
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(claims.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti");
        assertThat(generated.expiresIn()).isEqualTo(60);
        assertThat(generated.toString()).doesNotContain(generated.token()).contains("[REDACTED]");
    }

    @Test
    void generatesUniqueTokenIdentifiers() throws Exception {
        var signer = service();
        var first = SignedJWT.parse(signer.generateToken().token());
        var second = SignedJWT.parse(signer.generateToken().token());
        assertThat(first.getJWTClaimsSet().getJWTID()).isNotEqualTo(second.getJWTClaimsSet().getJWTID());
    }

    @Test
    void tamperingAndDifferentPublicKeyFailVerification() throws Exception {
        String token = service().generateToken().token();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8)
                .replace("user-service", "other-service");
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(SignedJWT.parse(String.join(".", parts))
                .verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isFalse();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        assertThat(SignedJWT.parse(token).verify(new RSASSAVerifier(
                (RSAPublicKey) generator.generateKeyPair().getPublic()))).isFalse();
    }

    @Test
    void generatedIssuerAndExpiryCanBeValidatedWithoutChangingUser() throws Exception {
        var generated = service().generateToken();
        var claims = SignedJWT.parse(generated.token()).getJWTClaimsSet();
        // Signature is verified separately above; these validators exercise time/issuer semantics only.
        Jwt jwt = Jwt.withTokenValue(generated.token()).header("alg", "RS256")
                .issuer(claims.getIssuer())
                .issuedAt(claims.getIssueTime().toInstant())
                .expiresAt(claims.getExpirationTime().toInstant()).build();
        assertThat(new JwtIssuerValidator("learning-service").validate(jwt).hasErrors()).isFalse();
        assertThat(new JwtIssuerValidator("user-service").validate(jwt).hasErrors()).isTrue();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC));
        assertThat(timestamps.validate(jwt).hasErrors()).isFalse();
        timestamps.setClock(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));
        assertThat(timestamps.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void loadsKeyOnceRatherThanReadingForEachToken() throws Exception {
        Path copy = writePem(pair.getPrivate().getEncoded(), "PRIVATE KEY");
        var signer = new InternalJwtTokenService(properties(), provider(copy));
        Files.delete(copy);
        assertThat(SignedJWT.parse(signer.generateToken().token())
                .verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
    }

    @Test
    void rejectsMissingInvalidAndPublicKeyFiles() throws Exception {
        Path invalid = directory.resolve("invalid.pem");
        Files.writeString(invalid, "-----BEGIN PRIVATE KEY-----\nnot-a-key\n-----END PRIVATE KEY-----");
        Path publicKey = writePem(pair.getPublic().getEncoded(), "PUBLIC KEY");
        for (Path path : new Path[]{directory.resolve("missing.pem"), invalid, publicKey}) {
            assertThatThrownBy(() -> provider(path))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to load Learning internal JWT signing key");
        }
    }

    @Test
    void rejectsWeakRsaAndNonRsaPrivateKeys() throws Exception {
        var weak = KeyPairGenerator.getInstance("RSA");
        weak.initialize(1024);
        Path weakPath = writePem(weak.generateKeyPair().getPrivate().getEncoded(), "PRIVATE KEY");
        var ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        Path ecPath = writePem(ec.generateKeyPair().getPrivate().getEncoded(), "PRIVATE KEY");
        for (Path path : new Path[]{weakPath, ecPath}) {
            assertThatThrownBy(() -> provider(path))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "301s", "500ms", "1500ms"})
    void rejectsInvalidLifetimes(String ttl) {
        enabledContext().withPropertyValues("app.security.internal-jwt.ttl=" + ttl).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void signingIsDisabledUnlessExplicitlyEnabled() {
        new ApplicationContextRunner().withUserConfiguration(InternalJwtConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(InternalJwtTokenService.class);
            assertThat(context).doesNotHaveBean(InternalSigningKeyProvider.class);
        });
    }

    @Test
    void enabledConfigurationBindsAndGeneratesToken() {
        enabledContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InternalJwtTokenService.class);
            assertThat(context.getBean(InternalJwtTokenService.class).generateToken().expiresIn()).isEqualTo(60);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience"})
    void enabledConfigurationRequiresAllIdentityAndKeySettings(String property) {
        enabledContext().withPropertyValues("app.security.internal-jwt." + property + "=").run(context ->
                assertThat(context).hasFailed());
    }

    private ApplicationContextRunner enabledContext() {
        return new ApplicationContextRunner().withUserConfiguration(InternalJwtConfig.class)
                .withPropertyValues("app.security.internal-jwt.enabled=true",
                        "app.security.internal-jwt.issuer=learning-service",
                        "app.security.internal-jwt.audience=user-service",
                        "app.security.signing.keys[0].kid=learning-rsa-v1",
                        "app.security.signing.keys[0].private-key-path=" + privatePath,
                        "app.security.signing.keys[0].active=true",
                        "app.security.internal-jwt.ttl=60s");
    }

    @Test
    void selectsActiveKeyFromMultipleKeysAndRotationChangesKidAndSignature() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var secondPair = generator.generateKeyPair();
        Path secondPath = writePem(secondPair.getPrivate().getEncoded(), "PRIVATE KEY");
        var v1 = key("learning-rsa-v1", privatePath, true);
        var v2 = key("learning-rsa-v2", secondPath, false);
        var original = new InternalJwtTokenService(properties(), new InternalSigningKeyProvider(keys(v1, v2)));
        var first = SignedJWT.parse(original.generateToken().token());
        assertThat(first.getHeader().getKeyID()).isEqualTo("learning-rsa-v1");
        assertThat(first.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();

        // Configuration is read on startup; simulate a restart with v2 selected.
        v1.setActive(false);
        v2.setActive(true);
        var rotated = new InternalJwtTokenService(properties(), new InternalSigningKeyProvider(keys(v1, v2)));
        var second = SignedJWT.parse(rotated.generateToken().token());
        assertThat(second.getHeader().getKeyID()).isEqualTo("learning-rsa-v2");
        assertThat(second.verify(new RSASSAVerifier((RSAPublicKey) secondPair.getPublic()))).isTrue();
        assertThat(second.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isFalse();
    }

    @Test
    void rejectsEmptyDuplicateMissingOrMultipleActiveKeys() {
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(key("v1", privatePath, false))))
                .hasMessage("Exactly one Learning RSA signing key must be active");
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(
                key("v1", privatePath, true), key("v2", privatePath, true))))
                .hasMessage("Exactly one Learning RSA signing key must be active");
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(
                key("v1", privatePath, true), key("v1", privatePath, false))))
                .hasMessage("Duplicate Learning RSA signing key kid");
    }

    @Test
    void validatesInactiveKeysAndRejectsBlankKeyConfiguration() {
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(
                key("v1", privatePath, true), key("v2", directory.resolve("missing.pem"), false))))
                .isInstanceOf(IllegalStateException.class);
        var blankKid = key(" ", privatePath, true);
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(blankKid)))
                .hasMessage("Learning RSA signing key kid must not be blank");
        var blankPath = key("v1", privatePath, true);
        blankPath.setPrivateKeyPath(" ");
        assertThatThrownBy(() -> new InternalSigningKeyProvider(keys(blankPath)))
                .hasMessage("Learning RSA private key path must not be blank");
    }

    @Test
    void springBindsMultipleIndexedKeys() {
        enabledContext().withPropertyValues(
                "app.security.signing.keys[1].kid=learning-rsa-v2",
                "app.security.signing.keys[1].private-key-path=" + privatePath,
                "app.security.signing.keys[1].active=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(InternalSigningKeyProperties.class).getKeys()).hasSize(2);
            assertThat(context.getBean(InternalSigningKeyProvider.class).getActiveSigningKey().kid())
                    .isEqualTo("learning-rsa-v1");
        });
    }

    @Test
    void bindsMultipleKeysFromCanonicalEnvironmentVariables() {
        enabledContext().withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                                "APP_SECURITY_SIGNING_KEYS_0_KID", "learning-rsa-v1",
                                "APP_SECURITY_SIGNING_KEYS_0_PRIVATEKEYPATH", privatePath.toString(),
                                "APP_SECURITY_SIGNING_KEYS_0_ACTIVE", "true",
                                "APP_SECURITY_SIGNING_KEYS_1_KID", "learning-rsa-v2",
                                "APP_SECURITY_SIGNING_KEYS_1_PRIVATEKEYPATH", privatePath.toString(),
                                "APP_SECURITY_SIGNING_KEYS_1_ACTIVE", "false"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(InternalSigningKeyProperties.class).getKeys()).hasSize(2);
                    assertThat(context.getBean(InternalSigningKeyProperties.class).getKeys().get(1).getPrivateKeyPath())
                            .isEqualTo(privatePath.toString());
                });
    }
}
