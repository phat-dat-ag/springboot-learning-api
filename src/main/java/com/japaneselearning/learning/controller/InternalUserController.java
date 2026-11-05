package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.config.OpenApiConfig;
import com.japaneselearning.learning.dto.response.InternalUserResponse;
import com.japaneselearning.learning.service.impl.InternalUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learning/internal-test")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/users/{id}")
    public InternalUserResponse getUser(@PathVariable UUID id) {
        return internalUserService.getUser(id);
    }
}
