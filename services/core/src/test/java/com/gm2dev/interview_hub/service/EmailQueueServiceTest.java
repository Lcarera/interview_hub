package com.gm2dev.interview_hub.service;

import tools.jackson.databind.ObjectMapper;
import com.gm2dev.interview_hub.config.CloudTasksProperties;
import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailQueueServiceTest {

    @Mock
    private CloudTasksClient cloudTasksClient;

    private EmailQueueService emailQueueService;
    private CloudTasksProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CloudTasksProperties(
                "test-project", "us-central1", "email-queue",
                true, "sa@test-project.iam.gserviceaccount.com",
                "http://localhost:8080", "http://localhost:8080"
        );
        emailQueueService = new EmailQueueService(
                cloudTasksClient, properties, new ObjectMapper()
        );
    }

    @Test
    void queueEmail_createsTaskWithPayload() {
        when(cloudTasksClient.createTask(any(String.class), any(Task.class)))
                .thenReturn(Task.getDefaultInstance());

        EmailTaskPayload payload = new EmailTaskPayload.VerificationEmail(
                "user@gm2dev.com", "token123"
        );

        emailQueueService.queueEmail(payload);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(cloudTasksClient).createTask(eq(properties.queuePath()), taskCaptor.capture());

        Task task = taskCaptor.getValue();
        assertThat(task.getHttpRequest().getUrl())
                .isEqualTo("http://localhost:8080/internal/email-worker");
    }

    @Test
    void queueEmail_includesOidcToken() {
        when(cloudTasksClient.createTask(any(String.class), any(Task.class)))
                .thenReturn(Task.getDefaultInstance());

        EmailTaskPayload payload = new EmailTaskPayload.PasswordResetEmail(
                "user@gm2dev.com", "reset-token"
        );

        emailQueueService.queueEmail(payload);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(cloudTasksClient).createTask(any(String.class), taskCaptor.capture());

        Task task = taskCaptor.getValue();
        assertThat(task.getHttpRequest().getOidcToken().getServiceAccountEmail())
                .isEqualTo("sa@test-project.iam.gserviceaccount.com");
        assertThat(task.getHttpRequest().getOidcToken().getAudience())
                .isEqualTo("http://localhost:8080");
    }
}
