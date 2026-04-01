package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.CloudTasksProperties;
import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails resendEmails;

    @Mock
    private CreateEmailResponse createEmailResponse;

    @Mock
    private EmailQueueService emailQueueService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(resend, "noreply@gm2dev.com", "http://localhost:4200", null, null);
    }

    @Test
    void sendDirectly_verificationEmail_sendsCorrectContent() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.VerificationEmail("user@gm2dev.com", "abc-token-123");
        emailService.sendDirectly(payload);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(resendEmails).send(captor.capture());

        CreateEmailOptions sent = captor.getValue();
        assertEquals("Interview Hub — Verify your email", sent.getSubject());
        assertTrue(sent.getHtml().contains("http://localhost:4200/auth/verify?token=abc-token-123"));
    }

    @Test
    void sendDirectly_passwordResetEmail_sendsCorrectContent() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.PasswordResetEmail("user@gm2dev.com", "reset-token-456");
        emailService.sendDirectly(payload);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(resendEmails).send(captor.capture());

        CreateEmailOptions sent = captor.getValue();
        assertEquals("Interview Hub — Reset your password", sent.getSubject());
        assertTrue(sent.getHtml().contains("http://localhost:4200/auth/reset-password?token=reset-token-456"));
    }

    @Test
    void sendDirectly_temporaryPasswordEmail_sendsCorrectContent() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.TemporaryPasswordEmail("user@gm2dev.com", "TempPass123");
        emailService.sendDirectly(payload);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(resendEmails).send(captor.capture());

        CreateEmailOptions sent = captor.getValue();
        assertEquals("Interview Hub — Your account has been created", sent.getSubject());
        assertTrue(sent.getHtml().contains("TempPass123"));
    }

    @Test
    void sendDirectly_shadowingApprovedEmail_sendsCorrectContent() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.ShadowingApprovedEmail(
                "shadower@example.com",
                "Java Interview - Jane Doe",
                "2026-03-20T10:00:00Z",
                "2026-03-20T11:00:00Z"
        );
        emailService.sendDirectly(payload);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(resendEmails).send(captor.capture());

        CreateEmailOptions sent = captor.getValue();
        assertEquals("Interview Hub — Shadowing Approved: Java Interview - Jane Doe", sent.getSubject());
        assertTrue(sent.getHtml().contains("Java Interview - Jane Doe"));
    }

    @Test
    void sendDirectly_verificationEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        var payload = new EmailTaskPayload.VerificationEmail("user@gm2dev.com", "token123");

        assertThrows(RuntimeException.class, () -> emailService.sendDirectly(payload));
    }

    @Test
    void sendDirectly_temporaryPasswordEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        var payload = new EmailTaskPayload.TemporaryPasswordEmail("user@gm2dev.com", "TmpPass1");

        assertThrows(RuntimeException.class, () -> emailService.sendDirectly(payload));
    }

    @Test
    void sendDirectly_passwordResetEmail_whenResendFails_doesNotThrow() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        var payload = new EmailTaskPayload.PasswordResetEmail("user@gm2dev.com", "reset-token");

        assertDoesNotThrow(() -> emailService.sendDirectly(payload));
    }

    @Test
    void sendDirectly_shadowingApprovedEmail_whenResendFails_doesNotThrow() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        var payload = new EmailTaskPayload.ShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z");

        assertDoesNotThrow(() -> emailService.sendDirectly(payload));
    }

    @Test
    void send_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        var payload = new EmailTaskPayload.VerificationEmail("user@gm2dev.com", "token");
        serviceWithQueue.send(payload);

        verify(emailQueueService).queueEmail(payload);
        verifyNoInteractions(resendEmails);
    }

    @Test
    void send_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.VerificationEmail("user@gm2dev.com", "token");
        emailService.send(payload);

        verify(resendEmails).send(any());
    }

    @Test
    void send_whenPropsEnabledButNoQueueService_sendsSynchronously() throws ResendException {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080", "http://localhost:8080"
        );
        EmailService serviceNoQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, null
        );

        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.PasswordResetEmail("user@gm2dev.com", "token");
        serviceNoQueue.send(payload);

        verify(resendEmails).send(any());
    }

    @Test
    void send_passwordResetEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        var payload = new EmailTaskPayload.PasswordResetEmail("user@gm2dev.com", "reset-token");
        serviceWithQueue.send(payload);

        verify(emailQueueService).queueEmail(payload);
    }

    @Test
    void send_temporaryPasswordEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        var payload = new EmailTaskPayload.TemporaryPasswordEmail("user@gm2dev.com", "TempPass123");
        serviceWithQueue.send(payload);

        verify(emailQueueService).queueEmail(payload);
    }

    @Test
    void send_shadowingApprovedEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        var payload = new EmailTaskPayload.ShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z"
        );
        serviceWithQueue.send(payload);

        verify(emailQueueService).queueEmail(payload);
    }

    @Test
    void send_shadowingApprovedEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.ShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z"
        );
        emailService.send(payload);

        verify(resendEmails).send(any());
    }

    @Test
    void send_passwordResetEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.PasswordResetEmail("user@gm2dev.com", "reset-token");
        emailService.send(payload);

        verify(resendEmails).send(any());
    }

    @Test
    void send_temporaryPasswordEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        var payload = new EmailTaskPayload.TemporaryPasswordEmail("user@gm2dev.com", "TempPass123");
        emailService.send(payload);

        verify(resendEmails).send(any());
    }
}
