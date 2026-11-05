package com.japaneselearning.learning.security;

import com.japaneselearning.learning.config.InternalJwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Generates service identity tokens only; does not send them or expose a token endpoint.
 */
@Slf4j
public final class InternalJwtTokenService {
    private final InternalJwtProperties properties;
    private final InternalSigningKeyProvider keys;
    private final Clock clock;

    public InternalJwtTokenService(
            InternalJwtProperties properties,
            InternalSigningKeyProvider keys
    ) {
        this(properties, keys, Clock.systemUTC());
    }

    InternalJwtTokenService(
            InternalJwtProperties properties,
            InternalSigningKeyProvider keys,
            Clock clock
    ) {
        this.properties = properties;
        this.keys = keys;
        this.clock = clock;
    }

    public GeneratedInternalToken generateToken() {
        var activeKey = keys.getActiveSigningKey();

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        var claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(properties.issuer())
                .audience(properties.audience())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(properties.ttl())))
                .jwtID(UUID.randomUUID().toString())
                .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("internal+jwt"))
                .keyID(activeKey.kid())
                .build();

        var jwt = new SignedJWT(header, claims);

        try {
            jwt.sign(new RSASSASigner(activeKey.privateKey()));

            return new GeneratedInternalToken(
                    jwt.serialize(),
                    properties.ttl().toSeconds()
            );
        } catch (JOSEException ex) {
            throw new IllegalStateException(
                    "Failed to generate internal JWT",
                    ex
            );
        }
    }

    public record GeneratedInternalToken(String token, long expiresIn) {
        @Override
        public String toString() {
            return "GeneratedInternalToken[token=[REDACTED], expiresIn=" + expiresIn + "]";
        }
    }
}
