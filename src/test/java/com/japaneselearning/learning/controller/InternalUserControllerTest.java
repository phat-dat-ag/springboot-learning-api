package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.common.exception.BusinessException;
import com.japaneselearning.learning.common.exception.ErrorCode;
import com.japaneselearning.learning.config.SecurityConfig;
import com.japaneselearning.learning.dto.InternalUserDTO;
import com.japaneselearning.learning.dto.response.InternalUserResponse;
import com.japaneselearning.learning.security.JwtRoleConverter;
import com.japaneselearning.learning.service.impl.InternalUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalUserController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class InternalUserControllerTest {
    private static final UUID ID = UUID.fromString("f05bf709-5cb8-4a4c-9ac4-e537b5579aef");
    private static final String PATH = "/api/v1/learning/internal-test/users/" + ID;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InternalUserService users;

    @MockitoBean
    JwtDecoder decoder;

    @Test
    void retainsExistingAuthenticationAndRoleRules() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(jwt().authorities(createAuthorityList("ROLE_OTHER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void returnsUnwrappedUserForAuthenticatedCaller() throws Exception {
        given(users.getUser(ID)).willReturn(new InternalUserResponse(new InternalUserDTO(
                ID, "test-user", "test@example.invalid", true, LocalDateTime.of(2026, 11, 1, 10, 0), null)));
        mvc.perform(get(PATH).with(jwt().authorities(createAuthorityList("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(ID.toString()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void upstreamFailuresRetainGeneric500Envelope() throws Exception {
        given(users.getUser(ID)).willThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        mvc.perform(get(PATH).with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
