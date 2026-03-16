package com.gm2dev.interview_hub.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class CloudTasksConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
    public CloudTasksClient cloudTasksClient() throws IOException {
        return CloudTasksClient.create();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "false", matchIfMissing = true)
    public CloudTasksClient noOpCloudTasksClient() {
        return null;
    }
}
