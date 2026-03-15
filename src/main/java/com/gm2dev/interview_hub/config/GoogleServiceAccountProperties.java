package com.gm2dev.interview_hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.google-service-account")
public class GoogleServiceAccountProperties {
    private String keyJson;
    private String calendarId = "primary";
}
