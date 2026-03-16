package com.gm2dev.interview_hub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTasksPropertiesTest {

    @Test
    void queuePath_formatsCorrectly() {
        CloudTasksProperties props = new CloudTasksProperties(
                "my-project", "us-central1", "email-queue",
                true, "sa@project.iam.gserviceaccount.com", "https://app.example.com"
        );

        assertThat(props.queuePath())
                .isEqualTo("projects/my-project/locations/us-central1/queues/email-queue");
    }

    @Test
    void hasValidServiceAccountEmail_returnsTrueForValidEmail() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@project.iam.gserviceaccount.com", "https://app.example.com"
        );

        assertThat(props.hasValidServiceAccountEmail()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void hasValidServiceAccountEmail_returnsFalseForNullOrBlank(String email) {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, email, "https://app.example.com"
        );

        assertThat(props.hasValidServiceAccountEmail()).isFalse();
    }

    @Test
    void hasValidAudience_returnsTrueForValidAudience() {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@project.iam.gserviceaccount.com", "https://app.example.com"
        );

        assertThat(props.hasValidAudience()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void hasValidAudience_returnsFalseForNullOrBlank(String audience) {
        CloudTasksProperties props = new CloudTasksProperties(
                "proj", "loc", "queue", true, "sa@project.iam.gserviceaccount.com", audience
        );

        assertThat(props.hasValidAudience()).isFalse();
    }
}
