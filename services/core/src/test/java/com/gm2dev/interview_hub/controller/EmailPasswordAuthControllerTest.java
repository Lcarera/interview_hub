package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.service.EmailPasswordAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailPasswordAuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class EmailPasswordAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailPasswordAuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void register_withValidRequest_returns201() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isCreated());

        verify(authService).register(any());
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"Password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withExistingEmail_returns409() throws Exception {
        doThrow(new IllegalStateException("An account with this email already exists"))
                .when(authService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void register_withNonGm2devEmail_returns403() throws Exception {
        doThrow(new SecurityException("Registration restricted"))
                .when(authService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gmail.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_withValidToken_returns200() throws Exception {
        mockMvc.perform(get("/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk());

        verify(authService).verifyEmail("valid-token");
    }

    @Test
    void verify_withInvalidToken_returns403() throws Exception {
        doThrow(new SecurityException("Invalid or expired token"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/auth/verify").param("token", "bad-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthResponse("jwt-token", 3600, "user@gm2dev.com"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("user@gm2dev.com"));
    }

    @Test
    void login_withInvalidCredentials_returns403() throws Exception {
        when(authService.login(any()))
                .thenThrow(new SecurityException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"WrongPass1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendVerification_returns200Always() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\"}"))
                .andExpect(status().isOk());

        verify(authService).resendVerification("user@gm2dev.com");
    }

    @Test
    void forgotPassword_returns200Always() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withValidRequest_returns200() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"reset-token\", \"newPassword\": \"NewPassword1\"}"))
                .andExpect(status().isOk());

        verify(authService).resetPassword(any());
    }

    @Test
    void resetPassword_withInvalidToken_returns403() throws Exception {
        doThrow(new SecurityException("Invalid or expired token"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"bad-token\", \"newPassword\": \"NewPassword1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allAuthEndpoints_arePublic() throws Exception {
        // No JWT needed - these should be publicly accessible
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@gm2dev.com\", \"password\": \"Password1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/verify").param("token", "any"))
                .andExpect(status().isOk());
    }
}
