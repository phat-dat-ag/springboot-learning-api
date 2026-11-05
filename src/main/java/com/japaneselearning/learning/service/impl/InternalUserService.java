package com.japaneselearning.learning.service.impl;

import com.japaneselearning.learning.client.UserServiceClient;
import com.japaneselearning.learning.dto.response.InternalUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalUserService {
    private final UserServiceClient userServiceClient;

    public InternalUserResponse getUser(UUID uuid) {
        return userServiceClient.getUser(uuid);
    }
}