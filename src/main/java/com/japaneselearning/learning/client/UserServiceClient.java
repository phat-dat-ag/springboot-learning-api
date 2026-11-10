package com.japaneselearning.learning.client;

import com.japaneselearning.learning.config.ServiceEndpointProperties;
import com.japaneselearning.learning.dto.response.InternalUserResponse;
import com.japaneselearning.learning.common.exception.BusinessException;
import com.japaneselearning.learning.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@Slf4j
public class UserServiceClient {

    private final RestClient restClient;
    private final ServiceEndpointProperties properties;

    public UserServiceClient(
            @Qualifier("userRestClient")
            RestClient restClient,

            ServiceEndpointProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public InternalUserResponse getUser(UUID uuid) {
        InternalUserResponse response;

        try {
            response = restClient
                    .get()
                    .uri(properties.getUser().getUser() + "/{id}", uuid)
                    .retrieve()
                    .body(InternalUserResponse.class);
        } catch (RestClientResponseException ex) {
            // Preserve the existing error contract without logging upstream bodies or user data.
            log.atWarn()
                    .addKeyValue("event", "dependency.user.failed")
                    .addKeyValue("upstream.status", ex.getStatusCode().value())
                    .log("User Service request failed");

            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.atWarn()
                    .addKeyValue("event", "dependency.user.failed")
                    .addKeyValue(
                            "upstream.errorType",
                            ex.getClass().getSimpleName()
                    ).log("User Service request failed");

            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (response == null
                || response.user() == null
                || !uuid.equals(response.user().id())
        ) {
            log.atWarn()
                    .addKeyValue("event", "dependency.user.invalid_response")
                    .log("User Service returned an invalid user response");

            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return response;
    }
}
