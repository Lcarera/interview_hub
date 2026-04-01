package com.gm2dev.shared.email;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailMessage.VerificationEmailMessage.class, name = "VERIFICATION"),
        @JsonSubTypes.Type(value = EmailMessage.PasswordResetEmailMessage.class, name = "PASSWORD_RESET"),
        @JsonSubTypes.Type(value = EmailMessage.TemporaryPasswordEmailMessage.class, name = "TEMPORARY_PASSWORD"),
        @JsonSubTypes.Type(value = EmailMessage.ShadowingApprovedEmailMessage.class, name = "SHADOWING_APPROVED")
})
public sealed interface EmailMessage permits
        EmailMessage.VerificationEmailMessage,
        EmailMessage.PasswordResetEmailMessage,
        EmailMessage.TemporaryPasswordEmailMessage,
        EmailMessage.ShadowingApprovedEmailMessage {

    String to();

    record VerificationEmailMessage(String to, String token) implements EmailMessage {}

    record PasswordResetEmailMessage(String to, String token) implements EmailMessage {}

    record TemporaryPasswordEmailMessage(String to, String temporaryPassword) implements EmailMessage {}

    record ShadowingApprovedEmailMessage(
            String to,
            String summary,
            String startTime,
            String endTime
    ) implements EmailMessage {}
}
