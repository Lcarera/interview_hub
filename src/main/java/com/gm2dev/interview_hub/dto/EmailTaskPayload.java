package com.gm2dev.interview_hub.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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

    record VerificationEmail(
            @NotBlank @Email String to,
            @NotBlank String token
    ) implements EmailTaskPayload {}

    record PasswordResetEmail(
            @NotBlank @Email String to,
            @NotBlank String token
    ) implements EmailTaskPayload {}

    record TemporaryPasswordEmail(
            @NotBlank @Email String to,
            @NotBlank String temporaryPassword
    ) implements EmailTaskPayload {}

    record ShadowingApprovedEmail(
            @NotBlank @Email String to,
            @NotBlank String summary,
            @NotBlank String startTime,
            @NotBlank String endTime
    ) implements EmailTaskPayload {}
}
