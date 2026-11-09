package com.japaneselearning.learning.security.jwt.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Same indexed signing-key configuration convention as User Service, with independent values.
 */
@ConfigurationProperties(prefix = "app.security.signing")
@Getter
@Setter
public class InternalSigningKeyProperties {
    private List<Key> keys = new ArrayList<>();

    @Getter
    @Setter
    public static class Key {
        private String kid;
        private String privateKeyPath;
        private boolean active;
    }
}
