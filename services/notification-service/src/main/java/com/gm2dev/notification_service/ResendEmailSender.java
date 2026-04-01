package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@EnableConfigurationProperties(ResendProperties.class)
public class ResendEmailSender {

    private final Resend resend;
    private final String fromEmail;
    private final EmailRenderer renderer;

    public ResendEmailSender(ResendProperties properties,
                             @Value("${app.mail.from}") String fromEmail,
                             EmailRenderer renderer) {
        this.resend = new Resend(properties.apiKey());
        this.fromEmail = fromEmail;
        this.renderer = renderer;
    }

    public void send(EmailMessage message) {
        try {
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(message.to())
                    .subject(renderer.subject(message))
                    .html(renderer.htmlBody(message))
                    .build();
            resend.emails().send(options);
            log.debug("Sent {} email to {}", message.getClass().getSimpleName(), message.to());
        } catch (ResendException e) {
            log.error("Failed to send {} email to {}", message.getClass().getSimpleName(), message.to(), e);
            // Do not rethrow — a failed email must not poison the RabbitMQ message
            // and cause infinite redelivery. Log and discard.
        }
    }
}
