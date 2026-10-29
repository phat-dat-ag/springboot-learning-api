package com.japaneselearning.learning.dto.response;

public record PublicKeyDTO(
        String kty,
        String use,
        String alg,
        String kid,
        String n,
        String e
) {
}
