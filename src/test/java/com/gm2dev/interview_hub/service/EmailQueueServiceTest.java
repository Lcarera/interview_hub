package com.gm2dev.interview_hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                true, "sa@test-project.iam.gserviceaccount.com", "http://localhost:8080"
        );
        emailQueueService = new EmailQueueService(
                cloudTasksClient, properties, new ObjectMapper(), "http://localhost:8080"
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
    void queueEmail_includesOidcToken_whenServiceAccountConfigured() {
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
    }

    @Test
    void queueEmail_omitsOidcToken_whenNoServiceAccount() {
        CloudTasksProperties noSaProps = new CloudTasksProperties(
                "test-project", "us-central1", "email-queue",
                true, null, "http://localhost:8080"
        );
        EmailQueueService noSaService = new EmailQueueService(
                cloudTasksClient, noSaProps, new ObjectMapper(), "http://localhost:8080"
        );

        when(cloudTasksClient.createTask(any(String.class), any(Task.class)))
                .thenReturn(Task.getDefaultInstance());

        EmailTaskPayload payload = new EmailTaskPayload.TemporaryPasswordEmail(
                "user@gm2dev.com", "TempPass123"
        );

        noSaService.queueEmail(payload);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(cloudTasksClient).createTask(any(String.class), taskCaptor.capture());

        Task task = taskCaptor.getValue();
        assertThat(task.getHttpRequest().hasOidcToken()).isFalse();
    }
}
