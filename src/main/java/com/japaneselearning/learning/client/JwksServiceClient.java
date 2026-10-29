package com.japaneselearning.learning.client;

import com.japaneselearning.learning.config.ServiceEndpointProperties;
import com.japaneselearning.learning.dto.response.PublicKeysResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JwksServiceClient {

    private final RestClient restClient;
    private final ServiceEndpointProperties properties;

    public JwksServiceClient(
            @Qualifier("jwksRestClient")
            RestClient restClient,

            ServiceEndpointProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public PublicKeysResponse getPublicKeys() {
        return restClient
                .get()
                .uri(properties.getJwks().getPublicKeys())
                .retrieve()
                .body(PublicKeysResponse.class);
    }
}