package com.gm2dev.interview_hub.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.util.HtmlUtils;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailTaskPayload.VerificationEmail.class, name = "VERIFICATION"),
        @JsonSubTypes.Type(value = EmailTaskPayload.PasswordResetEmail.class, name = "PASSWORD_RESET"),
        @JsonSubTypes.Type(value = EmailTaskPayload.TemporaryPasswordEmail.class, name = "TEMPORARY_PASSWORD"),
        @JsonSubTypes.Type(value = EmailTaskPayload.ShadowingApprovedEmail.class, name = "SHADOWING_APPROVED")
})
public sealed interface EmailTaskPayload permits
        EmailTaskPayload.VerificationEmail,
        EmailTaskPayload.PasswordResetEmail,
        EmailTaskPayload.TemporaryPasswordEmail,
        EmailTaskPayload.ShadowingApprovedEmail {

    @NotBlank @Email String to();

    String subject();

    String htmlBody(EmailRenderContext ctx);

    default boolean propagateFailure() { return true; }

    record VerificationEmail(
            @NotBlank @Email String to,
            @NotBlank String token
    ) implements EmailTaskPayload {
        @Override
        public String subject() {
            return "Interview Hub — Verify your email";
        }

        @Override
        public String htmlBody(EmailRenderContext ctx) {
            String link = ctx.frontendUrl() + "/auth/verify?token=" + token;
            return "<h2>Welcome to Interview Hub</h2>"
                    + "<p>Click the link below to verify your email address:</p>"
                    + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                    + "<p>This link expires in 24 hours.</p>";
        }
    }

    record PasswordResetEmail(
            @NotBlank @Email String to,
            @NotBlank String token
    ) implements EmailTaskPayload {
        @Override
        public String subject() {
            return "Interview Hub — Reset your password";
        }

        @Override
        public String htmlBody(EmailRenderContext ctx) {
            String link = ctx.frontendUrl() + "/auth/reset-password?token=" + token;
            return "<h2>Password Reset</h2>"
                    + "<p>Click the link below to reset your password:</p>"
                    + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                    + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
        }

        @Override
        public boolean propagateFailure() { return false; }
    }

    record TemporaryPasswordEmail(
            @NotBlank @Email String to,
            @NotBlank String temporaryPassword
    ) implements EmailTaskPayload {
        @Override
        public String subject() {
            return "Interview Hub — Your account has been created";
        }

        @Override
        public String htmlBody(EmailRenderContext ctx) {
            return "<h2>Welcome to Interview Hub</h2>"
                    + "<p>An admin has created an account for you.</p>"
                    + "<p>Your temporary password is: <strong>" + temporaryPassword + "</strong></p>"
                    + "<p>Please log in and change your password.</p>";
        }
    }

    record ShadowingApprovedEmail(
            @NotBlank @Email String to,
            @NotBlank String summary,
            @NotBlank String startTime,
            @NotBlank String endTime
    ) implements EmailTaskPayload {
        @Override
        public String subject() {
            return "Interview Hub — Shadowing Approved: " + summary;
        }

        @Override
        public String htmlBody(EmailRenderContext ctx) {
            String safeSummary = HtmlUtils.htmlEscape(summary);
            return "<h2>Shadowing Request Approved</h2>"
                    + "<p><strong>" + safeSummary + "</strong></p>"
                    + "<p>Start: " + HtmlUtils.htmlEscape(startTime) + "</p>"
                    + "<p>End: " + HtmlUtils.htmlEscape(endTime) + "</p>"
                    + "<p>You have been added to the calendar event as an attendee.</p>";
        }

        @Override
        public boolean propagateFailure() { return false; }
    }
}
