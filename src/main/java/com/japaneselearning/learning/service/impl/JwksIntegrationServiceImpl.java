package com.japaneselearning.learning.service.impl;

import com.japaneselearning.learning.client.JwksServiceClient;
import com.japaneselearning.learning.dto.response.PublicKeysResponse;
import com.japaneselearning.learning.service.JwksIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwksIntegrationServiceImpl implements JwksIntegrationService {

    private final JwksServiceClient jwksServiceClient;

    @Override
    public PublicKeysResponse getPublicKeys() {
        return jwksServiceClient.getPublicKeys();
    }
}