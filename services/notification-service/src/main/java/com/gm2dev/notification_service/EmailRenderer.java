package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailRenderer {

    private final String frontendUrl;

    public EmailRenderer(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String subject(EmailMessage message) {
        return switch (message) {
            case EmailMessage.VerificationEmailMessage ignored ->
                    "Interview Hub — Verify your email";
            case EmailMessage.PasswordResetEmailMessage ignored ->
                    "Interview Hub — Reset your password";
            case EmailMessage.TemporaryPasswordEmailMessage ignored ->
                    "Interview Hub — Your account has been created";
            case EmailMessage.ShadowingApprovedEmailMessage m ->
                    "Interview Hub — Shadowing Approved: " + m.summary();
        };
    }

    public String htmlBody(EmailMessage message) {
        return switch (message) {
            case EmailMessage.VerificationEmailMessage m -> renderVerification(m);
            case EmailMessage.PasswordResetEmailMessage m -> renderPasswordReset(m);
            case EmailMessage.TemporaryPasswordEmailMessage m -> renderTemporaryPassword(m);
            case EmailMessage.ShadowingApprovedEmailMessage m -> renderShadowingApproved(m);
        };
    }

    private String renderVerification(EmailMessage.VerificationEmailMessage m) {
        String link = frontendUrl + "/auth/verify?token=" + m.token();
        return "<h2>Welcome to Interview Hub</h2>"
                + "<p>Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p>";
    }

    private String renderPasswordReset(EmailMessage.PasswordResetEmailMessage m) {
        String link = frontendUrl + "/auth/reset-password?token=" + m.token();
        return "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
    }

    private String renderTemporaryPassword(EmailMessage.TemporaryPasswordEmailMessage m) {
        return "<h2>Welcome to Interview Hub</h2>"
                + "<p>An admin has created an account for you.</p>"
                + "<p>Your temporary password is: <strong>" + m.temporaryPassword() + "</strong></p>"
                + "<p>Please log in and change your password.</p>";
    }

    private String renderShadowingApproved(EmailMessage.ShadowingApprovedEmailMessage m) {
        String safeSummary = HtmlUtils.htmlEscape(m.summary());
        return "<h2>Shadowing Request Approved</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>Start: " + HtmlUtils.htmlEscape(m.startTime()) + "</p>"
                + "<p>End: " + HtmlUtils.htmlEscape(m.endTime()) + "</p>"
                + "<p>You have been added to the calendar event as an attendee.</p>";
    }
}
