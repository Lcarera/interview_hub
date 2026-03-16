package com.gm2dev.interview_hub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.interview_hub.config.CloudTasksProperties;
import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
@Slf4j
public class EmailQueueService {

    private final CloudTasksClient cloudTasksClient;
    private final CloudTasksProperties properties;
    private final ObjectMapper objectMapper;
    private final String workerUrl;

    public EmailQueueService(
            CloudTasksClient cloudTasksClient,
            CloudTasksProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.base-url}") String appBaseUrl
    ) {
        this.cloudTasksClient = cloudTasksClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.workerUrl = appBaseUrl + "/internal/email-worker";
    }

    public void queueEmail(EmailTaskPayload payload) {
        if (!properties.enabled()) {
            log.debug("Cloud Tasks disabled, skipping queue for email to {}", payload.to());
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                    .setUrl(workerUrl)
                    .setHttpMethod(HttpMethod.POST)
                    .putHeaders("Content-Type", "application/json")
                    .setBody(ByteString.copyFrom(jsonPayload, StandardCharsets.UTF_8));

            if (properties.serviceAccountEmail() != null && !properties.serviceAccountEmail().isBlank()) {
                httpRequestBuilder.setOidcToken(
                        OidcToken.newBuilder()
                                .setServiceAccountEmail(properties.serviceAccountEmail())
                                .build()
                );
            }

            Task task = Task.newBuilder()
                    .setHttpRequest(httpRequestBuilder.build())
                    .build();

            Task created = cloudTasksClient.createTask(properties.queuePath(), task);
            log.debug("Queued email task {} for {}", created.getName(), payload.to());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize email payload for {}", payload.to(), e);
            throw new RuntimeException("Failed to queue email", e);
        }
    }
}
