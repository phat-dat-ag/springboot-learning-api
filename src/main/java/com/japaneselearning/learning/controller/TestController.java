package com.japaneselearning.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Operation(
            summary = "Test request logging",
            description = "Returns Hello, test and logs the request with a correlation ID."
    )
    @GetMapping(value = "/test", produces = MediaType.TEXT_PLAIN_VALUE)
    public String test() {
        return "Hello, test";
    }
}
