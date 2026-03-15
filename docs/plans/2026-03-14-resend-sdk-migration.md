# Resend SDK Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace SMTP-based `JavaMailSender` with the Resend Java SDK to fix production email sending failures (#46).

**Architecture:** Swap the transport layer inside `EmailService` from Spring Mail SMTP to Resend HTTP API. The public API of `EmailService` stays identical so callers (`EmailPasswordAuthService`, `AdminService`) require zero changes. A `ResendConfig` bean makes the `Resend` client injectable and mockable.

**Tech Stack:** Java 25, Spring Boot 4.0.2, `com.resend:resend-java:4.11.0`, Mockito (tests)

---

### Task 1: Update dependencies in build.gradle

**Files:**
- Modify: `build.gradle:43-44`

**Step 1: Replace the mail dependency**

Change:
```gradle
	// Email
	implementation 'org.springframework.boot:spring-boot-starter-mail'
```

To:
```gradle
	// Email (Resend HTTP API)
	implementation 'com.resend:resend-java:4.11.0'
```

**Step 2: Verify the project compiles (expect failures — EmailService still references old imports)**

Run: `./gradlew compileJava 2>&1 | head -20`
Expected: Compilation errors in `EmailService.java` (missing `jakarta.mail`, `JavaMailSender` imports). This is expected — we fix it in Task 3.

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: replace spring-boot-starter-mail with resend-java SDK (#46)"
```

---

### Task 2: Update configuration files

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

**Step 1: Update application.yml**

Remove the entire `spring.mail` block (lines 27-39) and `management.health.mail.enabled` (lines 56-59). Add `resend.api-key` under the `app` section.

The full `application.yml` after changes:
```yaml
# Interview Hub backend configuration
spring:
  application:
    name: interview-hub

  datasource:
    url: ${DB_URL:jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:6543/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:3}
      minimum-idle: 1
      connection-timeout: 20000
      data-source-properties:
        prepareThreshold: 0

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false

app:
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${APP_BASE_URL:http://localhost:8080}/auth/google/callback
  jwt:
    signing-secret: ${JWT_SIGNING_SECRET}
    expiration-seconds: 3600
  mail:
    from: ${MAIL_FROM:noreply@lcarera.dev}
  resend:
    api-key: ${RESEND_API_KEY:}
  google-service-account:
    key-json: ${GOOGLE_SERVICE_ACCOUNT_KEY:}
    calendar-id: ${GOOGLE_CALENDAR_ID:primary}

logging:
  level:
    com.gm2dev.interview_hub: DEBUG
    org.springframework.security: DEBUG
```

**Step 2: Update application-test.yml**

Remove the `spring.mail` block (lines 15-17). Add `app.resend.api-key` with a test value.

The full `application-test.yml` after changes:
```yaml
spring:
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
  frontend-url: http://localhost:4200
  google:
    client-id: test-client-id
    client-secret: test-client-secret
    redirect-uri: http://localhost:8080/auth/google/callback
  jwt:
    signing-secret: test-signing-secret-that-is-at-least-32-bytes-long
    expiration-seconds: 3600
  mail:
    from: test@gm2dev.com
  resend:
    api-key: test-resend-api-key
  google-service-account:
    key-json: ""
    calendar-id: primary

logging:
  level:
    com.gm2dev.interview_hub: DEBUG
```

**Step 3: Commit**

```bash
git add src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "config: replace SMTP mail config with Resend API key (#46)"
```

---

### Task 3: Create ResendConfig and rewrite EmailService

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/config/ResendConfig.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/service/EmailService.java`

**Step 1: Create ResendConfig**

```java
package com.gm2dev.interview_hub.config;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResendConfig {

    @Bean
    public Resend resend(@Value("${app.resend.api-key}") String apiKey) {
        return new Resend(apiKey);
    }
}
```

**Step 2: Rewrite EmailService**

Replace the entire `EmailService.java` with:

```java
package com.gm2dev.interview_hub.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;
    private final String fromEmail;
    private final String appBaseUrl;

    public EmailService(Resend resend,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.frontend-url}") String appBaseUrl) {
        this.resend = resend;
        this.fromEmail = fromEmail;
        this.appBaseUrl = appBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/verify?token=" + token;
        String subject = "Interview Hub — Verify your email";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/reset-password?token=" + token;
        String subject = "Interview Hub — Reset your password";
        String body = "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
        sendHtmlEmailQuietly(toEmail, subject, body);
    }

    public void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword) {
        String subject = "Interview Hub — Your account has been created";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>An admin has created an account for you.</p>"
                + "<p>Your temporary password is: <strong>" + temporaryPassword + "</strong></p>"
                + "<p>Please log in and change your password.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            doSend(to, subject, htmlBody);
        } catch (ResendException e) {
            throw new RuntimeException("Failed to send email to " + to + " (subject: " + subject + ")", e);
        }
    }

    private void sendHtmlEmailQuietly(String to, String subject, String htmlBody) {
        try {
            doSend(to, subject, htmlBody);
        } catch (ResendException e) {
            log.error("Failed to send email to {} with subject: {}", to, subject, e);
        }
    }

    private void doSend(String to, String subject, String htmlBody) throws ResendException {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(htmlBody)
                .build();
        resend.emails().send(params);
        log.debug("Sent email to {} with subject: {}", to, subject);
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/config/ResendConfig.java src/main/java/com/gm2dev/interview_hub/service/EmailService.java
git commit -m "feat: replace JavaMailSender with Resend SDK in EmailService (#46)"
```

---

### Task 4: Rewrite EmailServiceTest

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java`

**Step 1: Write the updated tests**

Replace the entire `EmailServiceTest.java` with:

```java
package com.gm2dev.interview_hub.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.ResendEmails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private Resend resend;

    @Mock
    private ResendEmails resendEmails;

    @Mock
    private CreateEmailResponse createEmailResponse;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(resend, "noreply@gm2dev.com", "http://localhost:4200");
    }

    @Test
    void sendVerificationEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendVerificationEmail("user@gm2dev.com", "abc-token-123");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPasswordResetEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token-456");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendTemporaryPasswordEmail_sendsEmail() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class))).thenReturn(createEmailResponse);

        emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");

        verify(resendEmails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendVerificationEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertThrows(RuntimeException.class,
                () -> emailService.sendVerificationEmail("user@gm2dev.com", "token123"));
    }

    @Test
    void sendTemporaryPasswordEmail_whenResendFails_throwsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertThrows(RuntimeException.class,
                () -> emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TmpPass1"));
    }

    @Test
    void sendPasswordResetEmail_whenResendFails_doesNotThrow() throws ResendException {
        when(resend.emails()).thenReturn(resendEmails);
        when(resendEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token"));
    }
}
```

**Step 2: Run the tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailServiceTest`
Expected: All 6 tests pass.

**Step 3: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java
git commit -m "test: update EmailServiceTest to mock Resend SDK (#46)"
```

---

### Task 5: Run full test suite and verify

**Step 1: Run all tests**

Run: `./gradlew clean test --no-build-cache`
Expected: All tests pass. `EmailPasswordAuthServiceTest` and `AdminServiceTest` should pass unchanged since they mock `EmailService` at the service level.

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (includes JaCoCo coverage check)

---

### Task 6: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update Environment Variables section**

Remove these lines from the env vars list:
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_STARTTLS`
- `MAIL_SSL`

Replace with:
- `RESEND_API_KEY` - Resend API key for sending emails

Keep `MAIL_FROM` unchanged.

**Step 2: Update Dependencies section**

Change:
```
- Spring Boot Actuator
```

In the backend dependencies list, remove the line referencing mail if present. The `spring-boot-starter-mail` is implicitly removed since we replaced the dependency. Add `Resend Java SDK (email sending)` in its place.

**Step 3: Update the CI/CD env vars note if needed**

No CI/CD changes needed — `RESEND_API_KEY` is a runtime secret, not a CI secret.

**Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for Resend SDK migration (#46)"
```
