package com.gm2dev.interview_hub.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTasksPropertiesTest {

    @Test
    void queuePath_formatsCorrectly() {
        CloudTasksProperties props = new CloudTasksProperties(
                "my-project", "us-central1", "email-queue",
                true, "sa@project.iam.gserviceaccount.com",
                "https://app.example.com", "https://app.example.com"
        );

        assertThat(props.queuePath())
                .isEqualTo("projects/my-project/locations/us-central1/queues/email-queue");
    }
}
