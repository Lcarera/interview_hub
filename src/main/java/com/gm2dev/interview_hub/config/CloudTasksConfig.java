package com.gm2dev.interview_hub.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CloudTasksSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class CloudTasksConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
    public CloudTasksClient cloudTasksClient() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        log.info("Using Application Default Credentials for Cloud Tasks client");
        CloudTasksSettings settings = CloudTasksSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
        return CloudTasksClient.create(settings);
    }
}
