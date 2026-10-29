package com.japaneselearning.learning.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final Map<String, String> ROLES = Map.of(
            "1", "ROLE_USER",
            "2", "ROLE_ADMIN"
    );

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object role = jwt.getClaims().get("role");

        String authority = role == null ? null : ROLES.get(role.toString());

        return authority == null ? List.of() : List.of(new SimpleGrantedAuthority(authority));
    }
}
