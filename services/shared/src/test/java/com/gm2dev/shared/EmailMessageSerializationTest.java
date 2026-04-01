package com.gm2dev.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.shared.email.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMessageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldRoundTripVerificationEmail() throws Exception {
        var original = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.VerificationEmailMessage.class);
        var typed = (EmailMessage.VerificationEmailMessage) result;
        assertThat(typed.to()).isEqualTo("user@example.com");
        assertThat(typed.token()).isEqualTo("tok123");
    }

    @Test
    void shouldRoundTripPasswordResetEmail() throws Exception {
        var original = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.PasswordResetEmailMessage.class);
        assertThat(((EmailMessage.PasswordResetEmailMessage) result).to()).isEqualTo("reset@example.com");
    }

    @Test
    void shouldRoundTripTemporaryPasswordEmail() throws Exception {
        var original = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.TemporaryPasswordEmailMessage.class);
        assertThat(((EmailMessage.TemporaryPasswordEmailMessage) result).temporaryPassword()).isEqualTo("TmpPass1!");
    }

    @Test
    void shouldRoundTripShadowingApprovedEmail() throws Exception {
        var original = new EmailMessage.ShadowingApprovedEmailMessage(
            "shadow@example.com", "Java Interview - Alice", "2026-04-01T10:00", "2026-04-01T11:00");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.ShadowingApprovedEmailMessage.class);
        var typed = (EmailMessage.ShadowingApprovedEmailMessage) result;
        assertThat(typed.summary()).isEqualTo("Java Interview - Alice");
    }

    @Test
    void serializedJsonShouldContainTypeDiscriminator() throws Exception {
        var msg = new EmailMessage.VerificationEmailMessage("u@e.com", "t");
        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"type\":\"VERIFICATION\"");
    }
}
