package com.japaneselearning.learning.security;

import com.japaneselearning.learning.config.InternalSigningKeyProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;

/**
 * Loads Learning's own signing key once; inbound user JWT verification remains independent.
 */
public final class InternalSigningKeyProvider {
    private final SigningKey activeSigningKey;

    public InternalSigningKeyProvider(InternalSigningKeyProperties properties) {
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException(
                    "At least one Learning RSA signing key must be configured"
            );
        }

        var keyIds = new HashSet<String>();

        SigningKey active = null;

        for (var config : properties.getKeys()) {
            if (config == null
                    || config.getKid() == null
                    || config.getKid().isBlank()
            ) {
                throw new IllegalStateException(
                        "Learning RSA signing key kid must not be blank"
                );
            }

            if (!keyIds.add(config.getKid())) {
                throw new IllegalStateException(
                        "Duplicate Learning RSA signing key kid"
                );
            }

            if (config.getPrivateKeyPath() == null
                    || config.getPrivateKeyPath().isBlank()
            ) {
                throw new IllegalStateException(
                        "Learning RSA private key path must not be blank"
                );
            }

            RSAPrivateKey privateKey = readPrivateKey(config.getPrivateKeyPath());

            if (config.isActive()) {
                if (active != null) {
                    throw new IllegalStateException(
                            "Exactly one Learning RSA signing key must be active"
                    );
                }

                active = new SigningKey(config.getKid(), privateKey);
            }
        }

        if (active == null) {
            throw new IllegalStateException(
                    "Exactly one Learning RSA signing key must be active"
            );
        }

        activeSigningKey = active;
    }

    private RSAPrivateKey readPrivateKey(String filePath) {
        try {
            String pem = Files.readString(Path.of(filePath)).trim();

            if (!pem.startsWith("-----BEGIN PRIVATE KEY-----")
                    || !pem.endsWith("-----END PRIVATE KEY-----")) {
                throw new IllegalArgumentException(
                        "Internal signing key must be an unencrypted PKCS#8 PEM"
                );
            }

            byte[] encoded = Base64.getDecoder().decode(pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", ""));

            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));

            if (privateKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException(
                        "Internal signing key must be RSA with at least 2048 bits"
                );
            }

            return privateKey;
        } catch (IOException
                 | GeneralSecurityException
                 | IllegalArgumentException ex
        ) {
            throw new IllegalStateException(
                    "Failed to load Learning internal JWT signing key", ex
            );
        }
    }

    SigningKey getActiveSigningKey() {
        return activeSigningKey;
    }

    record SigningKey(String kid, RSAPrivateKey privateKey) {
        @Override
        public String toString() {
            return "SigningKey[kid=" + kid + ", privateKey=[REDACTED]]";
        }
    }
}
