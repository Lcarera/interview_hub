package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.CloudTasksProperties;
import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;
    private final String fromEmail;
    private final String appBaseUrl;
    private final CloudTasksProperties cloudTasksProperties;
    private final EmailQueueService emailQueueService;

    public EmailService(Resend resend,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.frontend-url}") String appBaseUrl,
                        @Nullable CloudTasksProperties cloudTasksProperties,
                        @Nullable @Autowired(required = false) EmailQueueService emailQueueService) {
        this.resend = resend;
        this.fromEmail = fromEmail;
        this.appBaseUrl = appBaseUrl;
        this.cloudTasksProperties = cloudTasksProperties;
        this.emailQueueService = emailQueueService;
    }

    public void queueVerificationEmail(String toEmail, String token) {
        if (isCloudTasksEnabled()) {
            emailQueueService.queueEmail(new EmailTaskPayload.VerificationEmail(toEmail, token));
        } else {
            sendVerificationEmail(toEmail, token);
        }
    }

    public void queuePasswordResetEmail(String toEmail, String token) {
        if (isCloudTasksEnabled()) {
            emailQueueService.queueEmail(new EmailTaskPayload.PasswordResetEmail(toEmail, token));
        } else {
            sendPasswordResetEmail(toEmail, token);
        }
    }

    public void queueTemporaryPasswordEmail(String toEmail, String temporaryPassword) {
        if (isCloudTasksEnabled()) {
            emailQueueService.queueEmail(new EmailTaskPayload.TemporaryPasswordEmail(toEmail, temporaryPassword));
        } else {
            sendTemporaryPasswordEmail(toEmail, temporaryPassword);
        }
    }

    public void queueShadowingApprovedEmail(String toEmail, String summary, String startTime, String endTime) {
        if (isCloudTasksEnabled()) {
            emailQueueService.queueEmail(new EmailTaskPayload.ShadowingApprovedEmail(toEmail, summary, startTime, endTime));
        } else {
            sendShadowingApprovedEmail(toEmail, summary, startTime, endTime);
        }
    }

    private boolean isCloudTasksEnabled() {
        return cloudTasksProperties != null && cloudTasksProperties.enabled() && emailQueueService != null;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/verify?token=" + token;
        String subject = "Interview Hub — Verify your email";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/reset-password?token=" + token;
        String subject = "Interview Hub — Reset your password";
        String body = "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
    }

    public void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword) {
        String subject = "Interview Hub — Your account has been created";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>An admin has created an account for you.</p>"
                + "<p>Your temporary password is: <strong>" + temporaryPassword + "</strong></p>"
                + "<p>Please log in and change your password.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }


    public void sendShadowingApprovedEmail(String toEmail, String summary, String startTime, String endTime) {
        String safeSummary = HtmlUtils.htmlEscape(summary);
        String subject = "Interview Hub — Shadowing Approved: " + summary;
        String body = "<h2>Shadowing Request Approved</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>Start: " + HtmlUtils.htmlEscape(startTime) + "</p>"
                + "<p>End: " + HtmlUtils.htmlEscape(endTime) + "</p>"
                + "<p>You have been added to the calendar event as an attendee.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            doSend(to, subject, htmlBody);
        } catch (ResendException e) {
            throw new RuntimeException("Failed to send email to " + to + " (subject: " + subject + ")", e);
        }
    }

    private void sendHtmlEmailQuietly(String to, String subject, String htmlBody) {
        try {
            doSend(to, subject, htmlBody);
        } catch (ResendException e) {
            log.error("Failed to send email to {} with subject: {}", to, subject, e);
        }
    }

    private void doSend(String to, String subject, String htmlBody) throws ResendException {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(htmlBody)
                .build();
        resend.emails().send(params);
        log.debug("Sent email to {} with subject: {}", to, subject);
    }
}
