package com.japaneselearning.learning.common.response;

import java.util.List;

public record APIError(
        List<ErrorDetail> details
) {

    public record ErrorDetail(
            String field,
            String code,
            String message
    ) {
    }
}