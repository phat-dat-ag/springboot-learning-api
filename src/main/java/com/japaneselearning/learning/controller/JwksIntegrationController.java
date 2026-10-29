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
        log.info("Received get all public keys request");

        PublicKeysResponse publicKeys = jwksIntegrationService.getPublicKeys();

        log.info(
                "Got all public key successfully, publicKeysCount={}",
                publicKeys.keys().size()
        );

        return success(publicKeys);
    }
}