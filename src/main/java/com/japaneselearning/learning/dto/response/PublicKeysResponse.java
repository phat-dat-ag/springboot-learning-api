package com.japaneselearning.learning.dto.response;

import java.util.List;

public record PublicKeysResponse(
        List<PublicKeyDTO> keys
) {
}