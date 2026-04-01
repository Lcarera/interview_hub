package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.gm2dev.interview_hub.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unlike other controller tests, this class intentionally does NOT use @Import(SecurityConfig.class).
 * When Cloud Tasks is enabled, SecurityConfig creates an internalEndpointsFilterChain bean that
 * instantiates a real GoogleIdTokenVerifier, which makes network calls to fetch Google's public keys.
 * Testing controller logic in isolation avoids this external dependency.
 */
@WebMvcTest(InternalEmailController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cloud-tasks.enabled=true")
class InternalEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void processEmailTask_withValidVerificationPayload_callsSendDirectly() throws Exception {
        String payload = """
            {"type":"VERIFICATION","to":"user@gm2dev.com","token":"abc123"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<EmailTaskPayload> captor = ArgumentCaptor.forClass(EmailTaskPayload.class);
        verify(emailService).sendDirectly(captor.capture());

        EmailTaskPayload captured = captor.getValue();
        assertInstanceOf(EmailTaskPayload.VerificationEmail.class, captured);
        assertEquals("user@gm2dev.com", captured.to());
    }

    @Test
    void processEmailTask_withValidPasswordResetPayload_callsSendDirectly() throws Exception {
        String payload = """
            {"type":"PASSWORD_RESET","to":"user@gm2dev.com","token":"reset-token"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<EmailTaskPayload> captor = ArgumentCaptor.forClass(EmailTaskPayload.class);
        verify(emailService).sendDirectly(captor.capture());

        EmailTaskPayload captured = captor.getValue();
        assertInstanceOf(EmailTaskPayload.PasswordResetEmail.class, captured);
    }

    @Test
    void processEmailTask_withValidTemporaryPasswordPayload_callsSendDirectly() throws Exception {
        String payload = """
            {"type":"TEMPORARY_PASSWORD","to":"user@gm2dev.com","temporaryPassword":"TempPass123"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<EmailTaskPayload> captor = ArgumentCaptor.forClass(EmailTaskPayload.class);
        verify(emailService).sendDirectly(captor.capture());

        EmailTaskPayload captured = captor.getValue();
        assertInstanceOf(EmailTaskPayload.TemporaryPasswordEmail.class, captured);
    }

    @Test
    void processEmailTask_withValidShadowingApprovedPayload_callsSendDirectly() throws Exception {
        String payload = """
            {"type":"SHADOWING_APPROVED","to":"shadower@gm2dev.com","summary":"Java Interview - Jane Doe","startTime":"2026-03-20T10:00:00Z","endTime":"2026-03-20T11:00:00Z"}
            """;

        mockMvc.perform(post("/internal/email-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<EmailTaskPayload> captor = ArgumentCaptor.forClass(EmailTaskPayload.class);
        verify(emailService).sendDirectly(captor.capture());

        EmailTaskPayload captured = captor.getValue();
        assertInstanceOf(EmailTaskPayload.ShadowingApprovedEmail.class, captured);
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
