package com.gm2dev.interview_hub.service;

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

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(resend, "noreply@gm2dev.com", "http://localhost:4200");
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
    void sendInterviewInviteEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendInterviewInviteEmail(
                "attendee@example.com",
                "Java Interview - Jane Doe",
                "2026-03-20T10:00:00Z",
                "2026-03-20T11:00:00Z",
                "https://meet.google.com/abc-defg-hij"
        );

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendInterviewUpdateEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendInterviewUpdateEmail(
                "attendee@example.com",
                "Java Interview - Jane Doe",
                "2026-03-21T10:00:00Z",
                "2026-03-21T11:00:00Z"
        );

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendInterviewCancellationEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendInterviewCancellationEmail(
                "attendee@example.com",
                "Java Interview - Jane Doe"
        );

        verify(resendEmails).send(any(CreateEmailOptions.class));
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
}
