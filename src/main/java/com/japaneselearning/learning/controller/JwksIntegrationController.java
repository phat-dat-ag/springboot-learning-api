package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.common.controller.BaseController;
import com.japaneselearning.learning.common.response.APIResponse;
import com.japaneselearning.learning.dto.response.PublicKeysResponse;
import com.japaneselearning.learning.service.JwksIntegrationService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration/jwks")
@RequiredArgsConstructor
@Slf4j
public class JwksIntegrationController extends BaseController {

    private final JwksIntegrationService jwksIntegrationService;

    @GetMapping()
    @Procedure(MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<@NonNull APIResponse<PublicKeysResponse>> getPublicKeys() {
        log.debug("Public key lookup requested");

        PublicKeysResponse publicKeys = jwksIntegrationService.getPublicKeys();

        log.atDebug()
                .addKeyValue("key_count", publicKeys.keys().size())
                .log("Public key lookup completed");

        return success(publicKeys);
    }
}