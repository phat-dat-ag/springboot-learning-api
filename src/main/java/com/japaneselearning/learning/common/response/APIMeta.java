package com.japaneselearning.learning.common.response;

import org.slf4j.MDC;

import java.time.Instant;

public record APIMeta(
        Instant timestamp,
        String correlationId,
        String traceId
) {

    public static APIMeta create() {
        return new APIMeta(
                Instant.now(),
                MDC.get("correlationId"),
                MDC.get("traceId")
        );
    }
}