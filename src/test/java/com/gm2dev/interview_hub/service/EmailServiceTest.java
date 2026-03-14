package com.gm2dev.interview_hub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

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
}
