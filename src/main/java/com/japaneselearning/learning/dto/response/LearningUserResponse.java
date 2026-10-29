package com.japaneselearning.learning.dto.response;

import java.util.List;

public record LearningUserResponse(
        String userId,
        List<String> authorities,
        String issuer
) {
}
