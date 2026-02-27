package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.http.MediaType;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void redirectToGoogle_returns302WithLocation() throws Exception {
        when(authService.buildAuthorizationUrl()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=test");

        mockMvc.perform(get("/auth/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://accounts.google.com/o/oauth2/v2/auth?client_id=test"));
    }

    @Test
    void callback_withValidCode_redirectsToFrontend() throws Exception {
        AuthResponse authResponse = new AuthResponse("jwt-token-value", 3600, "user@gm2dev.com");
        when(authService.handleCallback("valid-code")).thenReturn(authResponse);

        mockMvc.perform(get("/auth/google/callback").param("code", "valid-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/auth/callback#token=jwt-token-value")));
    }

    @Test
    void callback_withInvalidDomain_returns403() throws Exception {
        when(authService.handleCallback("bad-code"))
                .thenThrow(new SecurityException("Access restricted to @gm2dev.com accounts"));

        mockMvc.perform(get("/auth/google/callback").param("code", "bad-code"))
                .andExpect(status().isForbidden());
    }

    @Test
    void callback_withGoogleError_returns502() throws Exception {
        when(authService.handleCallback("error-code")).thenThrow(new IOException("Token exchange failed"));

        mockMvc.perform(get("/auth/google/callback").param("code", "error-code"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void token_withValidCode_returnsOAuthStandardResponse() throws Exception {
        AuthResponse authResponse = new AuthResponse("jwt-token-value", 3600, "user@gm2dev.com");
        when(authService.handleCallback("valid-code", "https://oauth.pstmn.io/v1/callback"))
                .thenReturn(authResponse);

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code")
                        .param("redirect_uri", "https://oauth.pstmn.io/v1/callback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-token-value"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void token_withInvalidDomain_returns403() throws Exception {
        when(authService.handleCallback("bad-code", "https://oauth.pstmn.io/v1/callback"))
                .thenThrow(new SecurityException("Access restricted to @gm2dev.com accounts"));

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "bad-code")
                        .param("redirect_uri", "https://oauth.pstmn.io/v1/callback"))
                .andExpect(status().isForbidden());
    }

    @Test
    void token_withGoogleError_returns502() throws Exception {
        when(authService.handleCallback("error-code", "https://oauth.pstmn.io/v1/callback"))
                .thenThrow(new IOException("Token exchange failed"));

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "error-code")
                        .param("redirect_uri", "https://oauth.pstmn.io/v1/callback"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void token_isPublicEndpoint() throws Exception {
        AuthResponse authResponse = new AuthResponse("jwt-token-value", 3600, "user@gm2dev.com");
        when(authService.handleCallback("some-code", "https://some-callback.com"))
                .thenReturn(authResponse);

        // No JWT needed - should be publicly accessible
        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "some-code")
                        .param("redirect_uri", "https://some-callback.com"))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpoints_arePublic() throws Exception {
        when(authService.buildAuthorizationUrl()).thenReturn("https://accounts.google.com");

        // No JWT needed - these should be publicly accessible
        mockMvc.perform(get("/auth/google"))
                .andExpect(status().isFound());
    }
}
