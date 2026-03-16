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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void sendVerificationEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendVerificationEmail("user@gm2dev.com", "abc-token-123");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPasswordResetEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token-456");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendTemporaryPasswordEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendVerificationEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertThrows(RuntimeException.class,
                () -> emailService.sendVerificationEmail("user@gm2dev.com", "token123"));
    }

    @Test
    void sendTemporaryPasswordEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertThrows(RuntimeException.class,
                () -> emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TmpPass1"));
    }

    @Test
    void sendShadowingApprovedEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendShadowingApprovedEmail(
                "shadower@example.com",
                "Java Interview - Jane Doe",
                "2026-03-20T10:00:00Z",
                "2026-03-20T11:00:00Z"
        );

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPasswordResetEmail_whenResendFails_doesNotThrow() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token"));
    }

    @Test
    void queueVerificationEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        serviceWithQueue.queueVerificationEmail("user@gm2dev.com", "token");

        verify(emailQueueService).queueEmail(any(EmailTaskPayload.VerificationEmail.class));
        verifyNoInteractions(resendEmails);
    }

    @Test
    void queueVerificationEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        CloudTasksProperties props = new CloudTasksProperties(
                null, null, null, false, null, null
        );
        EmailService serviceNoQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, null
        );

        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        serviceNoQueue.queueVerificationEmail("user@gm2dev.com", "token");

        verify(resendEmails).send(any());
    }

    @Test
    void queuePasswordResetEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        serviceWithQueue.queuePasswordResetEmail("user@gm2dev.com", "reset-token");

        verify(emailQueueService).queueEmail(any(EmailTaskPayload.PasswordResetEmail.class));
    }

    @Test
    void queueTemporaryPasswordEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        serviceWithQueue.queueTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");

        verify(emailQueueService).queueEmail(any(EmailTaskPayload.TemporaryPasswordEmail.class));
    }

    @Test
    void queueShadowingApprovedEmail_whenCloudTasksEnabled_queuesTask() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080"
        );
        EmailService serviceWithQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, emailQueueService
        );

        serviceWithQueue.queueShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z"
        );

        verify(emailQueueService).queueEmail(any(EmailTaskPayload.ShadowingApprovedEmail.class));
    }

    @Test
    void queueShadowingApprovedEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        emailService.queueShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z"
        );

        verify(resendEmails).send(any());
    }

    @Test
    void queuePasswordResetEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        emailService.queuePasswordResetEmail("user@gm2dev.com", "reset-token");

        verify(resendEmails).send(any());
    }

    @Test
    void queueTemporaryPasswordEmail_whenCloudTasksDisabled_sendsSynchronously() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        emailService.queueTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");

        verify(resendEmails).send(any());
    }

    @Test
    void queueVerificationEmail_whenPropsEnabledButNoQueueService_sendsSynchronously() throws ResendException {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@proj.iam.gserviceaccount.com", "http://localhost:8080"
        );
        EmailService serviceNoQueue = new EmailService(
                resend, "from@gm2dev.com", "http://localhost:4200", props, null
        );

        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any())).thenReturn(createEmailResponse);

        serviceNoQueue.queueVerificationEmail("user@gm2dev.com", "token");

        verify(resendEmails).send(any());
    }

    @Test
    void sendShadowingApprovedEmail_whenResendFails_doesNotThrow() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertDoesNotThrow(() -> emailService.sendShadowingApprovedEmail(
                "shadower@gm2dev.com", "Java Interview", "2026-03-20T10:00:00Z", "2026-03-20T11:00:00Z"));
    }
}
