package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.CloudTasksProperties;
import com.gm2dev.interview_hub.dto.EmailRenderContext;
import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService implements EmailSender {

    private final Resend resend;
    private final String fromEmail;
    private final EmailRenderContext renderContext;
    private final CloudTasksProperties cloudTasksProperties;
    private final EmailQueueService emailQueueService;

    public EmailService(Resend resend,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.frontend-url}") String frontendUrl,
                        @Nullable CloudTasksProperties cloudTasksProperties,
                        @Nullable EmailQueueService emailQueueService) {
        this.resend = resend;
        this.fromEmail = fromEmail;
        this.renderContext = new EmailRenderContext(frontendUrl);
        this.cloudTasksProperties = cloudTasksProperties;
        this.emailQueueService = emailQueueService;
    }

    @Override
    public void send(EmailTaskPayload payload) {
        if (isCloudTasksEnabled()) {
            emailQueueService.queueEmail(payload);
        } else {
            sendDirectly(payload);
        }
    }

    public void sendDirectly(EmailTaskPayload payload) {
        try {
            doSend(payload.to(), payload.subject(), payload.htmlBody(renderContext));
        } catch (ResendException e) {
            if (payload.propagateFailure()) {
                throw new RuntimeException("Failed to send email to " + payload.to(), e);
            }
            log.error("Failed to send email to {} with subject: {}", payload.to(), payload.subject(), e);
        }
    }

    private boolean isCloudTasksEnabled() {
        return cloudTasksProperties != null && cloudTasksProperties.enabled() && emailQueueService != null;
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
