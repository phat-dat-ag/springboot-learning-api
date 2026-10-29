package com.japaneselearning.learning.service;

import com.japaneselearning.learning.dto.response.PublicKeysResponse;

public interface JwksIntegrationService {

    PublicKeysResponse getPublicKeys();
}