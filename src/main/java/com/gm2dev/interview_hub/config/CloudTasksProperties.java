package com.gm2dev.interview_hub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.cloud-tasks")
@Validated
public record CloudTasksProperties(
        String projectId,
        String location,
        String queueId,
        boolean enabled,
        @NotBlank String serviceAccountEmail,
        @NotBlank String workerUrl,
        @NotBlank String audience
) {
    public String queuePath() {
        return String.format("projects/%s/locations/%s/queues/%s", projectId, location, queueId);
    }
}
