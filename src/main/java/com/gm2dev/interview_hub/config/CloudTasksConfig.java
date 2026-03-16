package com.gm2dev.interview_hub.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CloudTasksSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class CloudTasksConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
    public CloudTasksClient cloudTasksClient(GoogleServiceAccountProperties saProperties) throws IOException {
        final GoogleCredentials credentials = resolveCredentials(saProperties);
        CloudTasksSettings settings = CloudTasksSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
        return CloudTasksClient.create(settings);
    }

    private GoogleCredentials resolveCredentials(GoogleServiceAccountProperties saProperties) throws IOException {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            log.info("Using Application Default Credentials for Cloud Tasks client");
            return credentials;
        } catch (IOException e) {
            if (saProperties.getKeyJson() != null && !saProperties.getKeyJson().isBlank()) {
                log.info("ADC not available, using service account key for Cloud Tasks client");
                return ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(saProperties.getKeyJson().getBytes(StandardCharsets.UTF_8)));
            }
            throw e;
        }
    }
}
