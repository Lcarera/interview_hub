# Microservices Migration — Plan 2: Notification Service

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the email notification system from `services/core` into a new `notification-service` microservice. `core` publishes `EmailMessage` events to RabbitMQ via Spring Cloud Stream. `notification-service` consumes those events, renders HTML, and calls the Resend API. All Cloud Tasks infrastructure (queuing, OIDC auth, `InternalEmailController`) is deleted from `core`.

**Prerequisites:** Plan 1 (monorepo + shared module + Eureka) must be complete. `services/shared` must contain `EmailMessage` sealed interface at `com.gm2dev.shared.email.EmailMessage`. `services/core` must be at `services/core/src/...`. RabbitMQ must be in `compose.yaml`.

**Architecture:** `core` callers (`EmailPasswordAuthService`, `AdminService`, `ShadowingRequestService`) inject `EmailPublisher` and call `publish(EmailMessage)`. `EmailPublisher` uses Spring Cloud Stream `StreamBridge` to send to the `notification.emails` exchange on RabbitMQ. `notification-service` declares a `Consumer<EmailMessage>` bean named `processEmail` that Spring Cloud Stream binds to that exchange. `EmailRenderer` converts `EmailMessage` to (subject, htmlBody) pairs. `ResendEmailSender` calls the Resend HTTP API.

**Tech Stack:** Spring Boot 4.0.2, Spring Cloud Stream 2025.0.0, RabbitMQ binder, Resend Java SDK 4.11.0, Eureka client, Java 25.

> **Spring Cloud Compatibility Note:** This plan targets Spring Cloud `2025.0.0`. Verify the compatible version at https://spring.io/projects/spring-cloud#learn if `2025.0.0` does not resolve during Gradle sync.

---

## File Map

**Create:**
- `services/notification-service/build.gradle`
- `services/notification-service/src/main/java/com/gm2dev/notification_service/NotificationServiceApplication.java`
- `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailConsumer.java`
- `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailRenderer.java`
- `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendEmailSender.java`
- `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendProperties.java`
- `services/notification-service/src/main/resources/application.yml`
- `services/notification-service/src/test/java/com/gm2dev/notification_service/NotificationServiceApplicationTest.java`
- `services/notification-service/src/test/java/com/gm2dev/notification_service/EmailRendererTest.java`
- `services/notification-service/src/test/resources/application-test.yml`
- `services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPublisher.java`

**Delete from services/core:**
- `src/main/java/com/gm2dev/interview_hub/service/EmailSender.java`
- `src/main/java/com/gm2dev/interview_hub/service/EmailService.java`
- `src/main/java/com/gm2dev/interview_hub/service/EmailQueueService.java`
- `src/main/java/com/gm2dev/interview_hub/dto/EmailTaskPayload.java`
- `src/main/java/com/gm2dev/interview_hub/dto/EmailRenderContext.java`
- `src/main/java/com/gm2dev/interview_hub/controller/InternalEmailController.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksConfig.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksProperties.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksAuthenticationFilter.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksSecurityConfig.java`

**Modify:**
- `settings.gradle` — add `services:notification-service`
- `services/core/build.gradle` — remove `google-cloud-tasks` dep; add `spring-cloud-stream` + `spring-cloud-stream-binder-rabbit`; remove Cloud Tasks classes from JaCoCo exclusions
- `services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPasswordAuthService.java` — inject `EmailPublisher`, use `EmailMessage` types
- `services/core/src/main/java/com/gm2dev/interview_hub/service/AdminService.java` — inject `EmailPublisher`, use `EmailMessage` types
- `services/core/src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java` — inject `EmailPublisher`, use `EmailMessage` types
- `services/core/src/main/resources/application.yml` — remove `app.cloud-tasks`; add Spring Cloud Stream + RabbitMQ config
- `services/core/src/test/resources/application-test.yml` — remove `app.cloud-tasks`; add `StreamBridge` mock config
- `services/core/src/test/java/.../service/EmailPasswordAuthServiceTest.java` — mock `EmailPublisher` instead of `EmailSender`; use `EmailMessage` types in assertions
- `services/core/src/test/java/.../service/AdminServiceTest.java` — mock `EmailPublisher` instead of `EmailSender`; use `EmailMessage` types in assertions
- `services/core/src/test/java/.../service/ShadowingRequestServiceTest.java` — mock `EmailPublisher` instead of `EmailSender`; use `EmailMessage` types in assertions
- `compose.yaml` — add `notification-service` service

---

## Task 1: Create notification-service scaffold

**Files:**
- Create: `services/notification-service/build.gradle`
- Create: `services/notification-service/src/main/java/com/gm2dev/notification_service/NotificationServiceApplication.java`
- Create: `services/notification-service/src/main/resources/application.yml`
- Create: `services/notification-service/src/test/java/com/gm2dev/notification_service/NotificationServiceApplicationTest.java`
- Create: `services/notification-service/src/test/resources/application-test.yml`
- Modify: `settings.gradle`

- [ ] **Step 1: Write the failing context-loads test**

Create `services/notification-service/src/test/java/com/gm2dev/notification_service/NotificationServiceApplicationTest.java`:

```java
package com.gm2dev.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the notification-service Spring context starts without errors.
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails (class not found)**

```bash
./gradlew :services:notification-service:test 2>&1 | head -20
```

Expected: Build failure — `NotificationServiceApplication` does not exist (or project not found in settings).

- [ ] **Step 3: Add notification-service to settings.gradle**

Edit `settings.gradle` — append `include 'services:notification-service'` after `include 'services:eureka-server'`:

```groovy
rootProject.name = 'interview_hub'

include 'services:core'
include 'services:shared'
include 'services:eureka-server'
include 'services:notification-service'
```

- [ ] **Step 4: Create services/notification-service/build.gradle**

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation project(':services:shared')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-stream'
    implementation 'org.springframework.cloud:spring-cloud-stream-binder-rabbit'
    implementation 'com.resend:resend-java:4.11.0'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.cloud:spring-cloud-stream-test-binder'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('bootBuildImage') {
    imageName = "notification-service:${version}"
    environment = ['BP_JVM_VERSION': '25']
}
```

- [ ] **Step 5: Create NotificationServiceApplication**

Create `services/notification-service/src/main/java/com/gm2dev/notification_service/NotificationServiceApplication.java`:

```java
package com.gm2dev.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

- [ ] **Step 6: Create application.yml for notification-service**

Create `services/notification-service/src/main/resources/application.yml`:

```yaml
server:
  port: 8083

spring:
  application:
    name: notification-service
  cloud:
    stream:
      bindings:
        processEmail-in-0:
          destination: notification.emails
          group: notification-service
          content-type: application/json
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}

app:
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  mail:
    from: ${MAIL_FROM:noreply@lcarera.dev}
  resend:
    api-key: ${RESEND_API_KEY:}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health

logging:
  level:
    com.gm2dev.notification_service: DEBUG
```

- [ ] **Step 7: Create application-test.yml for notification-service**

Create `services/notification-service/src/test/resources/application-test.yml`:

```yaml
spring:
  cloud:
    stream:
      default-binder: test

eureka:
  client:
    enabled: false

app:
  frontend-url: http://localhost:4200
  mail:
    from: test@gm2dev.com
  resend:
    api-key: test-key
```

- [ ] **Step 8: Run the context-loads test**

```bash
./gradlew :services:notification-service:test --tests "com.gm2dev.notification_service.NotificationServiceApplicationTest"
```

Expected: 1 test passes. The context loads even without RabbitMQ running — the test binder replaces the rabbit binder in the `test` profile.

- [ ] **Step 9: Commit**

```bash
git add services/notification-service/ settings.gradle
git commit -m "chore: scaffold notification-service module"
```

---

## Task 2: Implement EmailRenderer with TDD

**Files:**
- Create: `services/notification-service/src/test/java/com/gm2dev/notification_service/EmailRendererTest.java`
- Create: `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailRenderer.java`

- [ ] **Step 1: Write the failing EmailRenderer tests**

Create `services/notification-service/src/test/java/com/gm2dev/notification_service/EmailRendererTest.java`:

```java
package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRendererTest {

    private EmailRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new EmailRenderer("http://localhost:4200");
    }

    // --- VerificationEmailMessage ---

    @Test
    void verificationEmail_subject_isCorrect() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Verify your email");
    }

    @Test
    void verificationEmail_htmlBody_containsVerificationLink() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("http://localhost:4200/auth/verify?token=tok123");
    }

    @Test
    void verificationEmail_htmlBody_containsWelcomeHeading() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.htmlBody(msg)).contains("Welcome to Interview Hub");
    }

    @Test
    void verificationEmail_htmlBody_mentionsExpiry() {
        var msg = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        assertThat(renderer.htmlBody(msg)).contains("24 hours");
    }

    // --- PasswordResetEmailMessage ---

    @Test
    void passwordResetEmail_subject_isCorrect() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Reset your password");
    }

    @Test
    void passwordResetEmail_htmlBody_containsResetLink() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("http://localhost:4200/auth/reset-password?token=reset-tok");
    }

    @Test
    void passwordResetEmail_htmlBody_containsIgnoreInstruction() {
        var msg = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        assertThat(renderer.htmlBody(msg)).contains("ignore this email");
    }

    // --- TemporaryPasswordEmailMessage ---

    @Test
    void temporaryPasswordEmail_subject_isCorrect() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Your account has been created");
    }

    @Test
    void temporaryPasswordEmail_htmlBody_containsTemporaryPassword() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.htmlBody(msg)).contains("TmpPass1!");
    }

    @Test
    void temporaryPasswordEmail_htmlBody_containsLoginInstruction() {
        var msg = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        assertThat(renderer.htmlBody(msg)).contains("change your password");
    }

    // --- ShadowingApprovedEmailMessage ---

    @Test
    void shadowingApprovedEmail_subject_containsSummary() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        assertThat(renderer.subject(msg)).isEqualTo("Interview Hub — Shadowing Approved: Java Interview - Alice");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_containsSummary() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        assertThat(renderer.htmlBody(msg)).contains("Java Interview - Alice");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_containsStartAndEndTimes() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview - Alice", "April 1, 2026 at 10:00 AM UTC", "April 1, 2026 at 11:00 AM UTC");
        String body = renderer.htmlBody(msg);
        assertThat(body).contains("April 1, 2026 at 10:00 AM UTC");
        assertThat(body).contains("April 1, 2026 at 11:00 AM UTC");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_escapesSummaryHtml() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "<script>alert('xss')</script>", "start", "end");
        String body = renderer.htmlBody(msg);
        assertThat(body).doesNotContain("<script>");
        assertThat(body).contains("&lt;script&gt;");
    }

    @Test
    void shadowingApprovedEmail_htmlBody_mentionsCalendarEvent() {
        var msg = new EmailMessage.ShadowingApprovedEmailMessage(
                "shadow@example.com", "Java Interview", "start", "end");
        assertThat(renderer.htmlBody(msg)).contains("calendar event");
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (class not found)**

```bash
./gradlew :services:notification-service:test --tests "com.gm2dev.notification_service.EmailRendererTest" 2>&1 | head -20
```

Expected: Compilation failure — `EmailRenderer` does not exist.

- [ ] **Step 3: Implement EmailRenderer**

Create `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailRenderer.java`:

```java
package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailRenderer {

    private final String frontendUrl;

    public EmailRenderer(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String subject(EmailMessage message) {
        return switch (message) {
            case EmailMessage.VerificationEmailMessage ignored ->
                    "Interview Hub — Verify your email";
            case EmailMessage.PasswordResetEmailMessage ignored ->
                    "Interview Hub — Reset your password";
            case EmailMessage.TemporaryPasswordEmailMessage ignored ->
                    "Interview Hub — Your account has been created";
            case EmailMessage.ShadowingApprovedEmailMessage m ->
                    "Interview Hub — Shadowing Approved: " + m.summary();
        };
    }

    public String htmlBody(EmailMessage message) {
        return switch (message) {
            case EmailMessage.VerificationEmailMessage m -> renderVerification(m);
            case EmailMessage.PasswordResetEmailMessage m -> renderPasswordReset(m);
            case EmailMessage.TemporaryPasswordEmailMessage m -> renderTemporaryPassword(m);
            case EmailMessage.ShadowingApprovedEmailMessage m -> renderShadowingApproved(m);
        };
    }

    private String renderVerification(EmailMessage.VerificationEmailMessage m) {
        String link = frontendUrl + "/auth/verify?token=" + m.token();
        return "<h2>Welcome to Interview Hub</h2>"
                + "<p>Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p>";
    }

    private String renderPasswordReset(EmailMessage.PasswordResetEmailMessage m) {
        String link = frontendUrl + "/auth/reset-password?token=" + m.token();
        return "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
    }

    private String renderTemporaryPassword(EmailMessage.TemporaryPasswordEmailMessage m) {
        return "<h2>Welcome to Interview Hub</h2>"
                + "<p>An admin has created an account for you.</p>"
                + "<p>Your temporary password is: <strong>" + m.temporaryPassword() + "</strong></p>"
                + "<p>Please log in and change your password.</p>";
    }

    private String renderShadowingApproved(EmailMessage.ShadowingApprovedEmailMessage m) {
        String safeSummary = HtmlUtils.htmlEscape(m.summary());
        return "<h2>Shadowing Request Approved</h2>"
                + "<p><strong>" + safeSummary + "</strong></p>"
                + "<p>Start: " + HtmlUtils.htmlEscape(m.startTime()) + "</p>"
                + "<p>End: " + HtmlUtils.htmlEscape(m.endTime()) + "</p>"
                + "<p>You have been added to the calendar event as an attendee.</p>";
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :services:notification-service:test --tests "com.gm2dev.notification_service.EmailRendererTest"
```

Expected: All 14 tests pass.

- [ ] **Step 5: Commit**

```bash
git add services/notification-service/src/
git commit -m "feat: implement EmailRenderer for notification-service"
```

---

## Task 3: Implement ResendEmailSender and EmailConsumer

**Files:**
- Create: `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendProperties.java`
- Create: `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendEmailSender.java`
- Create: `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailConsumer.java`

- [ ] **Step 1: Create ResendProperties**

Create `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendProperties.java`:

```java
package com.gm2dev.notification_service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(String apiKey) {}
```

- [ ] **Step 2: Create ResendEmailSender**

Create `services/notification-service/src/main/java/com/gm2dev/notification_service/ResendEmailSender.java`:

```java
package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@EnableConfigurationProperties(ResendProperties.class)
public class ResendEmailSender {

    private final Resend resend;
    private final String fromEmail;
    private final EmailRenderer renderer;

    public ResendEmailSender(ResendProperties properties,
                             @Value("${app.mail.from}") String fromEmail,
                             EmailRenderer renderer) {
        this.resend = new Resend(properties.apiKey());
        this.fromEmail = fromEmail;
        this.renderer = renderer;
    }

    public void send(EmailMessage message) {
        try {
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(message.to())
                    .subject(renderer.subject(message))
                    .html(renderer.htmlBody(message))
                    .build();
            resend.emails().send(options);
            log.debug("Sent {} email to {}", message.getClass().getSimpleName(), message.to());
        } catch (ResendException e) {
            log.error("Failed to send {} email to {}", message.getClass().getSimpleName(), message.to(), e);
            // Do not rethrow — a failed email must not poison the RabbitMQ message
            // and cause infinite redelivery. Log and discard.
        }
    }
}
```

- [ ] **Step 3: Create EmailConsumer**

Create `services/notification-service/src/main/java/com/gm2dev/notification_service/EmailConsumer.java`:

```java
package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class EmailConsumer {

    @Bean
    public Consumer<EmailMessage> processEmail(ResendEmailSender sender) {
        return message -> {
            log.debug("Received {} for {}", message.getClass().getSimpleName(), message.to());
            sender.send(message);
        };
    }
}
```

- [ ] **Step 4: Run context-loads test to confirm wiring**

```bash
./gradlew :services:notification-service:test --tests "com.gm2dev.notification_service.NotificationServiceApplicationTest"
```

Expected: 1 test passes. All beans wire up: `EmailConsumer`, `EmailRenderer`, `ResendEmailSender`.

- [ ] **Step 5: Commit**

```bash
git add services/notification-service/src/
git commit -m "feat: implement ResendEmailSender and EmailConsumer for notification-service"
```

---

## Task 4: Add EmailPublisher to core

**Files:**
- Create: `services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPublisher.java`
- Modify: `services/core/build.gradle`
- Modify: `services/core/src/main/resources/application.yml`
- Modify: `services/core/src/test/resources/application-test.yml`

- [ ] **Step 1: Add Spring Cloud Stream + rabbit binder to core's build.gradle**

In `services/core/build.gradle`, remove `com.google.cloud:google-cloud-tasks` and add the stream dependencies:

Replace:
```groovy
    // Google Cloud Tasks
    implementation 'com.google.cloud:google-cloud-tasks'
```

With:
```groovy
    // Spring Cloud Stream (RabbitMQ)
    implementation 'org.springframework.cloud:spring-cloud-stream'
    implementation 'org.springframework.cloud:spring-cloud-stream-binder-rabbit'
```

Also add the test binder for unit tests:
```groovy
    testImplementation 'org.springframework.cloud:spring-cloud-stream-test-binder'
```

- [ ] **Step 2: Create EmailPublisher**

Create `services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPublisher.java`:

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.shared.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailPublisher {

    private final StreamBridge streamBridge;

    public void publish(EmailMessage message) {
        log.debug("Publishing {} email for {}", message.getClass().getSimpleName(), message.to());
        streamBridge.send("email-out-0", message);
    }
}
```

- [ ] **Step 3: Update application.yml in core — remove cloud-tasks, add stream config**

In `services/core/src/main/resources/application.yml`:

Remove the entire `app.cloud-tasks` block:
```yaml
  cloud-tasks:
    project-id: ${GCP_PROJECT_ID:}
    location: ${GCP_LOCATION:us-central1}
    queue-id: ${CLOUD_TASKS_QUEUE_ID:email-queue}
    enabled: ${CLOUD_TASKS_ENABLED:false}
    service-account-email: ${CLOUD_TASKS_SA_EMAIL:}
    worker-url: ${CLOUD_TASKS_WORKER_URL:${APP_BASE_URL:http://localhost:8080}}
    audience: ${CLOUD_TASKS_AUDIENCE:${APP_BASE_URL:http://localhost:8080}}
```

Add Spring Cloud Stream and RabbitMQ config under the `spring:` key:

```yaml
  cloud:
    stream:
      bindings:
        email-out-0:
          destination: notification.emails
          content-type: application/json
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}
```

- [ ] **Step 4: Update application-test.yml in core — remove cloud-tasks, disable RabbitMQ auto-config**

In `services/core/src/test/resources/application-test.yml`:

Remove the `app.cloud-tasks` block:
```yaml
  cloud-tasks:
    enabled: false
    project-id: test-project
    location: us-central1
    queue-id: test-queue
    service-account-email: test@test.iam.gserviceaccount.com
    worker-url: http://localhost:8080
    audience: http://localhost:8080
```

Add Spring Cloud Stream test binder config:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
  cloud:
    stream:
      default-binder: test
```

The full `application-test.yml` for core should look like this after the edit:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
  cloud:
    stream:
      default-binder: test
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

app:
  base-url: http://localhost:8080
  frontend-url: http://localhost:4200
  google:
    client-id: test-client-id
    client-secret: test-client-secret
    redirect-uri: http://localhost:8080/auth/google/callback
    calendar:
      id: primary
      refresh-token: test-refresh-token
  jwt:
    signing-secret: test-signing-secret-that-is-at-least-32-bytes-long
    expiration-seconds: 3600
  mail:
    from: test@gm2dev.com
  resend:
    api-key: test-resend-api-key

eureka:
  client:
    enabled: false

logging:
  level:
    com.gm2dev.interview_hub: DEBUG
```

- [ ] **Step 5: Run core tests to confirm compilation**

```bash
./gradlew :services:core:compileJava :services:core:compileTestJava 2>&1 | tail -20
```

Expected: Compilation succeeds. (Tests will fail at runtime until the callers are updated in the next task — skip running tests for now.)

- [ ] **Step 6: Commit**

```bash
git add services/core/build.gradle services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPublisher.java services/core/src/main/resources/application.yml services/core/src/test/resources/application-test.yml
git commit -m "feat: add EmailPublisher to core for RabbitMQ email dispatch"
```

---

## Task 5: Update core callers to use EmailPublisher

**Files:**
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/service/EmailPasswordAuthService.java`
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/service/AdminService.java`
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java`

- [ ] **Step 1: Update EmailPasswordAuthService**

Replace all references to `EmailSender` and `EmailTaskPayload` with `EmailPublisher` and `EmailMessage`. The constructor parameter changes from `EmailSender emailSender` to `EmailPublisher emailPublisher`. The three `emailSender.send(new EmailTaskPayload.XxxEmail(...))` call sites become `emailPublisher.publish(new EmailMessage.XxxEmailMessage(...))`.

The updated field declarations and constructor:
```java
    private final EmailPublisher emailPublisher;

    public EmailPasswordAuthService(ProfileRepository profileRepository,
                                     VerificationTokenRepository verificationTokenRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmailPublisher emailPublisher,
                                     ProfileMapper profileMapper,
                                     JwtService jwtService) {
        this.profileRepository = profileRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailPublisher = emailPublisher;
        this.profileMapper = profileMapper;
        this.jwtService = jwtService;
    }
```

Replace the three call sites:

In `register()`:
```java
emailPublisher.publish(new EmailMessage.VerificationEmailMessage(request.email(), rawToken));
```

In `resendVerification()`:
```java
emailPublisher.publish(new EmailMessage.VerificationEmailMessage(email, rawToken));
```

In `forgotPassword()`:
```java
emailPublisher.publish(new EmailMessage.PasswordResetEmailMessage(email, rawToken));
```

Also update imports — remove `EmailTaskPayload`, add:
```java
import com.gm2dev.shared.email.EmailMessage;
```

- [ ] **Step 2: Update AdminService**

Replace `EmailSender emailSender` field and constructor parameter with `EmailPublisher emailPublisher`. Update the `createUser()` call site:

```java
emailPublisher.publish(new EmailMessage.TemporaryPasswordEmailMessage(request.email(), temporaryPassword));
```

Update imports — remove `EmailTaskPayload`, add:
```java
import com.gm2dev.shared.email.EmailMessage;
```

- [ ] **Step 3: Update ShadowingRequestService**

Replace `EmailSender emailSender` field with `EmailPublisher emailPublisher`. Update the `approveShadowingRequest()` call site:

```java
emailPublisher.publish(new EmailMessage.ShadowingApprovedEmailMessage(
        request.getShadower().getEmail(),
        summary,
        EMAIL_DATE_FMT.format(interview.getStartTime()),
        EMAIL_DATE_FMT.format(interview.getEndTime())));
```

Update imports — remove `EmailTaskPayload`, add:
```java
import com.gm2dev.shared.email.EmailMessage;
```

- [ ] **Step 4: Run core compilation**

```bash
./gradlew :services:core:compileJava 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL — no references to `EmailSender` or `EmailTaskPayload` remain in the callers.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/gm2dev/interview_hub/service/
git commit -m "refactor: switch core callers from EmailSender to EmailPublisher with EmailMessage types"
```

---

## Task 6: Update core tests

**Files:**
- Modify: `services/core/src/test/java/com/gm2dev/interview_hub/service/EmailPasswordAuthServiceTest.java`
- Modify: `services/core/src/test/java/com/gm2dev/interview_hub/service/AdminServiceTest.java`
- Modify: `services/core/src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java`

- [ ] **Step 1: Run core tests to see which ones fail**

```bash
./gradlew :services:core:test 2>&1 | grep -E "FAILED|ERROR|tests were run" | head -30
```

Expected: Tests in `EmailPasswordAuthServiceTest`, `AdminServiceTest`, and `ShadowingRequestServiceTest` fail because they still reference `EmailSender` and `EmailTaskPayload`.

- [ ] **Step 2: Update EmailPasswordAuthServiceTest**

The test uses `@ExtendWith(MockitoExtension.class)`. Replace:
- `@Mock EmailSender emailSender` → `@Mock EmailPublisher emailPublisher`
- Constructor call: pass `emailPublisher` instead of `emailSender`
- `ArgumentCaptor<EmailTaskPayload>` → `ArgumentCaptor<EmailMessage>`
- All `EmailTaskPayload.VerificationEmail` casts → `EmailMessage.VerificationEmailMessage`
- All `EmailTaskPayload.PasswordResetEmail` casts → `EmailMessage.PasswordResetEmailMessage`
- `verify(emailSender).send(...)` → `verify(emailPublisher).publish(...)`
- `verify(emailSender, never()).send(...)` → `verify(emailPublisher, never()).publish(...)`
- `doThrow(...).when(emailSender).send(...)` → `doThrow(...).when(emailPublisher).publish(...)`

Update imports — remove `EmailSender`, `EmailTaskPayload`; add:
```java
import com.gm2dev.interview_hub.service.EmailPublisher;
import com.gm2dev.shared.email.EmailMessage;
```

The constructor call in `@BeforeEach`:
```java
service = new EmailPasswordAuthService(
        profileRepository, verificationTokenRepository,
        passwordEncoder, emailPublisher, profileMapper, jwtService);
```

Example updated assertion in `register_withValidGm2devEmail_createsProfileAndSendsVerification`:
```java
ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
verify(emailPublisher).publish(emailCaptor.capture());
EmailMessage.VerificationEmailMessage email = (EmailMessage.VerificationEmailMessage) emailCaptor.getValue();
assertEquals("user@gm2dev.com", email.to());
assertNotNull(email.token());
```

And in `register_whenVerificationEmailFails_throwsRuntimeException`:
```java
doThrow(new RuntimeException("Email delivery failed"))
        .when(emailPublisher).publish(any(EmailMessage.class));
```

- [ ] **Step 3: Update AdminServiceTest**

Replace:
- `@Mock EmailSender emailSender` → `@Mock EmailPublisher emailPublisher`
- Constructor call to pass `emailPublisher`
- All `emailSender.send(...)` verifications → `emailPublisher.publish(...)`
- All `EmailTaskPayload.TemporaryPasswordEmail` casts → `EmailMessage.TemporaryPasswordEmailMessage`

Update imports — remove `EmailSender`, `EmailTaskPayload`; add:
```java
import com.gm2dev.interview_hub.service.EmailPublisher;
import com.gm2dev.shared.email.EmailMessage;
```

- [ ] **Step 4: Update ShadowingRequestServiceTest**

This test uses `@SpringBootTest` with `@MockitoBean`. Replace:
- `@MockitoBean EmailSender emailSender` → `@MockitoBean EmailPublisher emailPublisher`
- `verify(emailSender).send(...)` → `verify(emailPublisher).publish(...)`
- All `EmailTaskPayload.ShadowingApprovedEmail` references → `EmailMessage.ShadowingApprovedEmailMessage`

Update imports — remove `EmailSender`, `EmailTaskPayload`; add:
```java
import com.gm2dev.interview_hub.service.EmailPublisher;
import com.gm2dev.shared.email.EmailMessage;
```

- [ ] **Step 5: Run all core tests**

```bash
./gradlew :services:core:test
```

Expected: All tests pass. No references to `EmailSender`, `EmailService`, `EmailQueueService`, or `EmailTaskPayload` remain in test code.

- [ ] **Step 6: Commit**

```bash
git add services/core/src/test/
git commit -m "test: update core tests to use EmailPublisher and EmailMessage types"
```

---

## Task 7: Delete Cloud Tasks code from core

**Files to delete from services/core:**
- `src/main/java/com/gm2dev/interview_hub/service/EmailSender.java`
- `src/main/java/com/gm2dev/interview_hub/service/EmailService.java`
- `src/main/java/com/gm2dev/interview_hub/service/EmailQueueService.java`
- `src/main/java/com/gm2dev/interview_hub/dto/EmailTaskPayload.java`
- `src/main/java/com/gm2dev/interview_hub/dto/EmailRenderContext.java`
- `src/main/java/com/gm2dev/interview_hub/controller/InternalEmailController.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksConfig.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksProperties.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksAuthenticationFilter.java`
- `src/main/java/com/gm2dev/interview_hub/config/CloudTasksSecurityConfig.java`

Also delete related test files that exclusively test deleted code:
- `src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java`
- `src/test/java/com/gm2dev/interview_hub/service/EmailQueueServiceTest.java`

- [ ] **Step 1: Delete Cloud Tasks source files**

```bash
rm services/core/src/main/java/com/gm2dev/interview_hub/service/EmailSender.java
rm services/core/src/main/java/com/gm2dev/interview_hub/service/EmailService.java
rm services/core/src/main/java/com/gm2dev/interview_hub/service/EmailQueueService.java
rm services/core/src/main/java/com/gm2dev/interview_hub/dto/EmailTaskPayload.java
rm services/core/src/main/java/com/gm2dev/interview_hub/dto/EmailRenderContext.java
rm services/core/src/main/java/com/gm2dev/interview_hub/controller/InternalEmailController.java
rm services/core/src/main/java/com/gm2dev/interview_hub/config/CloudTasksConfig.java
rm services/core/src/main/java/com/gm2dev/interview_hub/config/CloudTasksProperties.java
rm services/core/src/main/java/com/gm2dev/interview_hub/config/CloudTasksAuthenticationFilter.java
rm services/core/src/main/java/com/gm2dev/interview_hub/config/CloudTasksSecurityConfig.java
```

- [ ] **Step 2: Delete Cloud Tasks test files**

```bash
rm services/core/src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java
rm services/core/src/test/java/com/gm2dev/interview_hub/service/EmailQueueServiceTest.java
```

- [ ] **Step 3: Remove Cloud Tasks exclusions from JaCoCo in core's build.gradle**

In `services/core/build.gradle`, remove from both `jacocoTestReport` and `jacocoTestCoverageVerification` exclusion lists:
```groovy
'**/CloudTasksConfig.class',
'**/CloudTasksAuthenticationFilter.class',
'**/CloudTasksSecurityConfig.class',
'**/EmailQueueService.class'
```

Also remove `InternalEmailController` test from coverage exclusions if it was listed (it was not — it was excluded via `@ConditionalOnProperty` at runtime but not in JaCoCo config, so no change needed there).

- [ ] **Step 4: Check SecurityConfig no longer references CloudTasks**

```bash
grep -r "CloudTasks\|InternalEmail\|EmailSender\|EmailService\|EmailQueueService\|EmailTaskPayload\|EmailRenderContext\|cloud-tasks" services/core/src/ --include="*.java" --include="*.yml"
```

Expected: Zero matches. If any remain, fix them before proceeding.

- [ ] **Step 5: Run core tests**

```bash
./gradlew :services:core:test
```

Expected: All tests pass. The deleted classes are gone and nothing references them.

- [ ] **Step 6: Run core JaCoCo coverage check**

```bash
./gradlew :services:core:check
```

Expected: BUILD SUCCESSFUL — 95% branch coverage maintained.

- [ ] **Step 7: Commit**

```bash
git add -A services/core/
git commit -m "chore: delete Cloud Tasks email infrastructure from core"
```

---

## Task 8: Update Docker Compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Add notification-service to compose.yaml**

Add the `notification-service` service after `eureka-server`. The final `compose.yaml` (building on Plan 1's version with `rabbitmq` and `eureka-server` already present):

```yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  eureka-server:
    image: eureka-server:0.0.1-SNAPSHOT
    ports:
      - "8761:8761"
    environment:
      EUREKA_HOSTNAME: eureka-server
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  notification-service:
    image: notification-service:0.0.1-SNAPSHOT
    environment:
      RABBITMQ_HOST: rabbitmq
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      MAIL_FROM: ${MAIL_FROM:-noreply@lcarera.dev}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost}
      EUREKA_URL: http://eureka-server:8761/eureka/
    depends_on:
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy

  app:
    image: interview-hub:0.0.1-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
      DB_URL: ${DB_URL}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      JWT_SIGNING_SECRET: ${JWT_SIGNING_SECRET}
      APP_BASE_URL: ${APP_BASE_URL:-http://localhost:8080}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost}
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      MAIL_FROM: ${MAIL_FROM:-noreply@lcarera.dev}
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
      RABBITMQ_HOST: rabbitmq
      EUREKA_URL: http://eureka-server:8761/eureka/
    env_file:
      - path: .env
        required: false
    depends_on:
      eureka-server:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      args:
        NG_CONFIG: docker
    ports:
      - "80:80"
    depends_on:
      - app
```

Note: `notification-service` does not expose a port externally — it only consumes from RabbitMQ and is discoverable via Eureka. The `app` service no longer has Cloud Tasks env vars.

- [ ] **Step 2: Build the notification-service image**

```bash
./gradlew :services:notification-service:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/notification-service:0.0.1-SNAPSHOT'`

- [ ] **Step 3: Rebuild the core image**

```bash
./gradlew :services:core:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/interview-hub:0.0.1-SNAPSHOT'`

- [ ] **Step 4: Start the stack**

```bash
docker compose up rabbitmq eureka-server notification-service app -d
```

- [ ] **Step 5: Verify notification-service registers with Eureka**

Open http://localhost:8761. Expected: Both `CORE` and `NOTIFICATION-SERVICE` listed under registered instances.

- [ ] **Step 6: Smoke test email flow**

Use the Postman collection (`postman/`) to register a new user via `POST /auth/register`. Expected: the `notification-service` container logs show a `Received VerificationEmailMessage` line, and the Resend API is called (check Resend dashboard or look for the sent email).

- [ ] **Step 7: Commit**

```bash
git add compose.yaml
git commit -m "chore: add notification-service to docker compose; remove cloud-tasks env vars from app"
```

---

## Task 9: Deploy Notification Service to GCP Cloud Run

**Files:**
- Modify: `infra/secrets.py`
- Delete: `infra/cloudtasks.py`
- Modify: `infra/cloudrun.py`
- Modify: `infra/__main__.py`
- Modify: `.github/workflows/deploy.yml`

> **Why always-on:** notification-service holds a persistent TCP connection to CloudAMQP. If it scales to zero, nobody consumes the RabbitMQ queue and emails queue indefinitely. `min_instance_count=1` keeps the consumer alive at ~$5/month.
>
> **Why delete `cloudtasks.py`:** Plan 2 removes all Cloud Tasks application code. Keeping the queue in GCP with no consumer wastes resources and creates confusing stack drift.

- [ ] **Step 1: Add `RABBITMQ_URL` to `infra/secrets.py`**

Add `"RABBITMQ_URL"` to the `_SECRET_NAMES` list:

```python
_SECRET_NAMES = [
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "GOOGLE_CLIENT_ID",
    "GOOGLE_CLIENT_SECRET",
    "JWT_SIGNING_SECRET",
    "RESEND_API_KEY",
    "GOOGLE_CALENDAR_REFRESH_TOKEN",
    "RABBITMQ_URL",  # CloudAMQP AMQP URL (amqps://user:pass@host/vhost)
]
```

- [ ] **Step 2: Delete `infra/cloudtasks.py`**

```bash
rm infra/cloudtasks.py
```

Pulumi will detect the removed resource on next `pulumi up` and destroy the Cloud Tasks queue and IAM binding.

- [ ] **Step 3: Update `infra/__main__.py`**

Remove the cloudtasks import and export. Add notification_service:

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects
from secrets import secrets  # noqa: F401 — imported for side effects
from cloudrun import backend_service, frontend_service, eureka_service, notification_service

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
pulumi.export("eureka_url", eureka_service.uri)
pulumi.export("notification_url", notification_service.uri)
```

- [ ] **Step 4: Update `infra/cloudrun.py`**

**4a.** Remove the cloudtasks import at the top:

```python
# Remove this line:
from cloudtasks import email_queue, cloudtasks_enqueuer
```

**4b.** Remove `cloudtasks_enqueuer` from `backend_service`'s `opts` and all Cloud Tasks env vars. The `opts` line becomes:

```python
opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
```

Remove these env vars from `backend_service`:
- `GCP_PROJECT_ID`
- `GCP_LOCATION`
- `CLOUD_TASKS_QUEUE_ID`
- `CLOUD_TASKS_ENABLED`
- `CLOUD_TASKS_SA_EMAIL`
- `CLOUD_TASKS_WORKER_URL`
- `CLOUD_TASKS_AUDIENCE`

Also remove this line near the top of the file:
```python
cloudtasks_worker_url = config.get("cloudtasks_worker_url")
```

**4c.** Add `notification_service` after `eureka_service`. The notification service reads `RESEND_API_KEY` and `RABBITMQ_URL` from Secret Manager:

```python
notification_image = config.get("notification_image") or "placeholder"

_notification_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name=name,
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secrets[name].secret_id,
                version="latest",
            )
        ),
    )
    for name in ["RESEND_API_KEY", "RABBITMQ_URL"]
]

notification_service = gcp.cloudrunv2.Service(
    "notification-service",
    name="interview-hub-notification",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=1),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=notification_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8080)],
                envs=_notification_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="EUREKA_URL",
                        value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="MAIL_FROM",
                        value="noreply@lcarera.dev",
                    ),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
                ),
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8080,
                    ),
                    initial_delay_seconds=15,
                    period_seconds=5,
                    failure_threshold=20,
                ),
            )
        ],
    ),
)

gcp.cloudrunv2.ServiceIamMember(
    "notification-service-invoker",
    project=project,
    location=region,
    name=notification_service.name,
    role="roles/run.invoker",
    member="allUsers",
)
```

- [ ] **Step 5: Update `.github/workflows/deploy.yml`**

**5a.** Add `notification` filter and output in the `changes` job:

```yaml
outputs:
  # ... existing outputs ...
  notification: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.notification }}
```

```yaml
filters: |
  # ... existing filters ...
  notification:
    - 'services/notification-service/**'
```

**5b.** Add notification to the `deploy` job `if` condition:

```yaml
if: ... || needs.changes.outputs.notification == 'true'
```

**5c.** Add build-and-push step (after the backend build step):

```yaml
- name: Build and push notification image
  if: needs.changes.outputs.notification == 'true'
  run: |
    IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/notification:${{ github.sha }}
    ./gradlew :services:notification-service:bootBuildImage --imageName=$IMAGE
    docker push $IMAGE
```

**5d.** In the "Deploy with Pulumi" step, add notification image config:

```bash
if [ "${{ needs.changes.outputs.notification }}" == "true" ]; then
  pulumi config set interview-hub-infra:notification_image \
    ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/notification:${{ github.sha }}
else
  CURRENT=$(gcloud run services describe interview-hub-notification \
    --region=${{ env.GCP_REGION }} --format='value(spec.template.spec.containers[0].image)' 2>/dev/null || echo "")
  if [ -n "$CURRENT" ]; then
    pulumi config set interview-hub-infra:notification_image "$CURRENT"
  fi
fi
```

- [ ] **Step 6: Set `RABBITMQ_URL` secret value manually**

After `pulumi up` creates the `interview-hub-rabbitmq-url` secret, set its value using the CloudAMQP URL from your CloudAMQP dashboard (AMQP URI format: `amqps://user:pass@hostname/vhost`):

```bash
echo -n "amqps://YOUR_USER:YOUR_PASS@YOUR_HOST/YOUR_VHOST" | \
  gcloud secrets versions add interview-hub-rabbitmq-url --data-file=-
```

- [ ] **Step 7: Verify with `pulumi preview`**

```bash
cd infra
source venv/bin/activate
pulumi stack select prod
pulumi preview
```

Expected: Preview shows `+ interview-hub-notification` to create, `- email-queue` and `- cloudtasks-enqueuer` to destroy, `~ interview-hub-backend` to update (removed Cloud Tasks env vars). No errors.

- [ ] **Step 8: Commit**

```bash
git add infra/secrets.py infra/cloudrun.py infra/__main__.py .github/workflows/deploy.yml
git rm infra/cloudtasks.py
git commit -m "feat: deploy notification-service to Cloud Run; remove Cloud Tasks infra"
```

---

## Self-Review

**Spec coverage:**
- All Cloud Tasks code deleted from `core`: `EmailSender`, `EmailService`, `EmailQueueService`, `EmailTaskPayload`, `EmailRenderContext`, `InternalEmailController`, `CloudTasksConfig`, `CloudTasksProperties`, `CloudTasksAuthenticationFilter`, `CloudTasksSecurityConfig`
- `EmailPublisher` added to `core` using `StreamBridge` targeting `email-out-0` → `notification.emails` exchange
- All three callers updated: `EmailPasswordAuthService`, `AdminService`, `ShadowingRequestService`
- All three caller tests updated to mock `EmailPublisher` and use `EmailMessage` subtypes
- `notification-service` module created with `EmailRenderer`, `ResendEmailSender`, `EmailConsumer`
- `EmailRenderer` has 14 unit tests covering all four email types (subject, body content, HTML escaping)
- Context-loads test confirms full Spring wiring
- `settings.gradle` includes `services:notification-service`
- `compose.yaml` includes `notification-service` with correct RabbitMQ and Eureka deps
- Core JaCoCo exclusions cleaned up (Cloud Tasks classes removed)
- Core 95% branch coverage enforced via `./gradlew :services:core:check`

**What this plan does NOT do** (handled in subsequent plans):
- Plan 3: Extract `GoogleCalendarService` into `calendar-service`; wire `core` to call `calendar-service` via OpenFeign
- Plan 4: Add Spring Cloud Gateway; migrate nginx API routing to Gateway
