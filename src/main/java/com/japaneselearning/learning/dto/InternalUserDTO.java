package com.japaneselearning.learning.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalUserDTO(
        UUID id,
        String username,
        String email,
        boolean isActivated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
