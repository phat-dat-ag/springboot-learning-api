package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.common.controller.BaseController;
import com.japaneselearning.learning.common.exception.BusinessException;
import com.japaneselearning.learning.common.exception.ErrorCode;
import com.japaneselearning.learning.common.response.APIResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController extends BaseController {

    @Operation(
            summary = "Test API responses and request logging",
            description = "Returns Hello, test in the standard response envelope. Set error=true to test business exception handling."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successful response with correlation metadata"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Structured INVALID_REQUEST error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = APIResponse.class)
            )
    )
    @GetMapping(
            value = "/test",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<@NonNull APIResponse<String>> test(
            @Parameter(description = "Set true to return a demonstration business error")
            @RequestParam(defaultValue = "false")
            boolean error
    ) {
        if (error) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return success("Hello, test");
    }
}
