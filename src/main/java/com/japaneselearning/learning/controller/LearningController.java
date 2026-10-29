package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.common.controller.BaseController;
import com.japaneselearning.learning.common.response.APIResponse;
import com.japaneselearning.learning.dto.response.LearningUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningController extends BaseController {
    @GetMapping("/me")
    public ResponseEntity<APIResponse<LearningUserResponse>> me(
            JwtAuthenticationToken authentication
    ) {
        return success(new LearningUserResponse(
                authentication.getToken().getSubject(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                authentication.getToken().getClaimAsString("iss")
        ));
    }
}
