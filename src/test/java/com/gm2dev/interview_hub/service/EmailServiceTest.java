package com.gm2dev.interview_hub.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@gm2dev.com", "http://localhost:4200");
    }

    @Test
    void sendVerificationEmail_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.sendVerificationEmail("user@gm2dev.com", "abc-token-123");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token-456");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendTemporaryPasswordEmail_sendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendVerificationEmail_whenMailSenderFails_throwsRuntimeException() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class,
                () -> emailService.sendVerificationEmail("user@gm2dev.com", "token123"));
    }

    @Test
    void sendTemporaryPasswordEmail_whenMailSenderFails_throwsRuntimeException() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class,
                () -> emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TmpPass1"));
    }

    @Test
    void sendPasswordResetEmail_whenMailSenderFails_doesNotThrow() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token"));
    }
}
