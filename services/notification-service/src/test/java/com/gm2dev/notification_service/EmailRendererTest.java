package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRendererTest {

    private EmailRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new EmailRenderer("http://localhost:4200");
    }

    // --- VerificationEmailMessage ---

    @Test
    void verificationEmail_subject_isCorrect() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Verify your email");
    }

    @Test
    void verificationEmail_htmlBody_containsVerificationLink() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("http://localhost:4200/auth/verify?token=tok123");
    }

    @Test
    void verificationEmail_htmlBody_containsWelcomeHeading() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.htmlBody(msg)).contains("Welcome to Interview Hub");
    }

    @Test
    void verificationEmail_htmlBody_mentionsExpiry() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.htmlBody(msg)).contains("24 hours");
    }

    // --- PasswordResetEmailMessage ---

    @Test
    void passwordResetEmail_subject_isCorrect() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Reset your password");
    }

    @Test
    void passwordResetEmail_htmlBody_containsResetLink() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("http://localhost:4200/auth/reset-password?token=reset-tok");
    }

    @Test
    void passwordResetEmail_htmlBody_containsIgnoreInstruction() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        assertThat(renderer.htmlBody(msg)).contains("ignore this email");
    }

    // --- TemporaryPasswordEmailMessage ---

    @Test
    void temporaryPasswordEmail_subject_isCorrect() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Your account has been created");
    }

    @Test
    void temporaryPasswordEmail_htmlBody_containsTemporaryPassword() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.htmlBody(msg)).contains("TmpPass1!");
    }

    @Test
    void temporaryPasswordEmail_htmlBody_containsLoginInstruction() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.htmlBody(msg)).contains("change your password");
    }

    // --- ShadowingApprovedEmailMessage ---

    @Test
    void shadowingApprovedEmail_subject_containsSummary() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Shadowing Approved: Java Interview - Alice");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_containsSummary() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        assertThat(renderer.htmlBody(msg)).contains("Java Interview - Alice");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_containsStartAndEndTimes() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("April 1, 2026 at 10:00 AM UTC");
        assertThat(body).contains("April 1, 2026 at 11:00 AM UTC");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_escapesSummaryHtml() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "<script>alert('xss')</script>", "start", "end");
        String body = renderer.htmlBody(msg);
        assertThat(body).doesNotContain("<script>");
        assertThat(body).contains("&lt;script&gt;");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_mentionsCalendarEvent() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview", "start", "end");
        assertThat(renderer.htmlBody(msg)).contains("calendar event");
    }
}
