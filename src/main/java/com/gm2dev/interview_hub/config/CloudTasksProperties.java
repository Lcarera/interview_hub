package com.gm2dev.interview_hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cloud-tasks")
public record CloudTasksProperties(
        String projectId,
        String location,
        String queueId,
        boolean enabled,
        String serviceAccountEmail,
        String workerUrl,
        String audience
) {
    public String queuePath() {
        return String.format("projects/%s/locations/%s/queues/%s", projectId, location, queueId);
    }

    public boolean hasValidServiceAccountEmail() {
        return serviceAccountEmail != null && !serviceAccountEmail.isBlank();
    }

    public boolean hasValidWorkerUrl() {
        return workerUrl != null && !workerUrl.isBlank();
    }

    public boolean hasValidAudience() {
        return audience != null && !audience.isBlank();
    }
}
