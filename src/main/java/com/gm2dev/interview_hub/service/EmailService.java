package com.gm2dev.interview_hub.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;
    private final String fromEmail;
    private final String appBaseUrl;

    public EmailService(Resend resend,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.frontend-url}") String appBaseUrl) {
        this.resend = resend;
        this.fromEmail = fromEmail;
        this.appBaseUrl = appBaseUrl;
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

    public void sendInterviewInviteEmail(String toEmail, String summary, String startTime, String endTime, String meetLink) {
        String safeSummary = HtmlUtils.htmlEscape(summary);
        String subject = "Interview Hub — Interview Scheduled: " + summary;
        String body = "<h2>Interview Scheduled</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>Start: " + HtmlUtils.htmlEscape(startTime) + "</p>"
                + "<p>End: " + HtmlUtils.htmlEscape(endTime) + "</p>"
                + (meetLink != null ? "<p>Google Meet: <a href=\"" + HtmlUtils.htmlEscape(meetLink) + "\">" + HtmlUtils.htmlEscape(meetLink) + "</a></p>" : "")
                + "<p>This event has been added to the shared interview calendar.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
    }

    public void sendInterviewUpdateEmail(String toEmail, String summary, String startTime, String endTime) {
        String safeSummary = HtmlUtils.htmlEscape(summary);
        String subject = "Interview Hub — Interview Updated: " + summary;
        String body = "<h2>Interview Updated</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>New Start: " + HtmlUtils.htmlEscape(startTime) + "</p>"
                + "<p>New End: " + HtmlUtils.htmlEscape(endTime) + "</p>"
                + "<p>The calendar event has been updated.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
    }

    public void sendInterviewCancellationEmail(String toEmail, String summary) {
        String safeSummary = HtmlUtils.htmlEscape(summary);
        String subject = "Interview Hub — Interview Cancelled: " + summary;
        String body = "<h2>Interview Cancelled</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>This interview has been cancelled and removed from the calendar.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
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
