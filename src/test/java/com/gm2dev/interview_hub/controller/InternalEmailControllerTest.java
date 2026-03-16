package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.service.EmailService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalEmailController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class InternalEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void processEmailTask_withValidVerificationPayload_sendsEmail() throws Exception {
        String payload = """
            {"type":"VERIFICATION","to":"user@gm2dev.com","token":"abc123"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(emailService).sendVerificationEmail("user@gm2dev.com", "abc123");
    }

    @Test
    void processEmailTask_withValidPasswordResetPayload_sendsEmail() throws Exception {
        String payload = """
            {"type":"PASSWORD_RESET","to":"user@gm2dev.com","token":"reset-token"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordResetEmail("user@gm2dev.com", "reset-token");
    }

    @Test
    void processEmailTask_withValidTemporaryPasswordPayload_sendsEmail() throws Exception {
        String payload = """
            {"type":"TEMPORARY_PASSWORD","to":"user@gm2dev.com","temporaryPassword":"TempPass123"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(emailService).sendTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");
    }

    @Test
    void processEmailTask_withValidShadowingApprovedPayload_sendsEmail() throws Exception {
        String payload = """
            {"type":"SHADOWING_APPROVED","to":"shadower@gm2dev.com","summary":"Java Interview - Jane Doe","startTime":"2026-03-20T10:00:00Z","endTime":"2026-03-20T11:00:00Z"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(emailService).sendShadowingApprovedEmail(
                "shadower@gm2dev.com",
                "Java Interview - Jane Doe",
                "2026-03-20T10:00:00Z",
                "2026-03-20T11:00:00Z"
        );
    }

    @Test
    void processEmailTask_withInvalidPayload_returnsBadRequest() throws Exception {
        String payload = """
            {"type":"VERIFICATION","to":"invalid-email","token":"abc123"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailService);
    }

    @Test
    void processEmailTask_withMissingRequiredField_returnsBadRequest() throws Exception {
        String payload = """
            {"type":"VERIFICATION","to":"user@gm2dev.com"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailService);
    }
}
