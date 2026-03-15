# Replace SMTP JavaMailSender with Resend Java SDK

**Date:** 2026-03-14
**Issue:** #46
**Status:** Approved

## Problem

Email sending via SMTP (`JavaMailSender` + `smtp.resend.com`) fails in production with `535 Authentication credentials invalid`. The same API key works via Resend's HTTP API, indicating an SMTP compatibility issue.

## Solution

Replace `spring-boot-starter-mail` / `JavaMailSender` with the Resend Java SDK (`com.resend:resend-java:4.11.0`), which uses Resend's HTTP API directly.

## Approach

**Approach A: Direct Resend SDK replacement** was chosen over HTTP-via-RestClient (more boilerplate) and SMTP debugging (fragile, already tested).

## Design

### 1. Dependency Changes

**`build.gradle`:**
- Remove: `org.springframework.boot:spring-boot-starter-mail`
- Add: `com.resend:resend-java:4.11.0`

### 2. Configuration Changes

**`application.yml`:**
- Remove the entire `spring.mail.*` block (host, port, username, password, smtp properties)
- Remove `management.health.mail.enabled: false`
- Keep `app.mail.from` (reads from `MAIL_FROM`)
- Add `app.resend.api-key: ${RESEND_API_KEY:}`

**`application-test.yml`:**
- Remove `spring.mail.host` and `spring.mail.port`

**Environment variables:**
- Remove: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_SSL`
- Add: `RESEND_API_KEY`
- Keep: `MAIL_FROM`

### 3. Resend Configuration Bean

New `ResendConfig` class:

```java
@Configuration
public class ResendConfig {
    @Bean
    public Resend resend(@Value("${app.resend.api-key}") String apiKey) {
        return new Resend(apiKey);
    }
}
```

### 4. EmailService Rewrite

Public interface unchanged — three methods:
- `sendVerificationEmail(String toEmail, String token)`
- `sendPasswordResetEmail(String toEmail, String token)`
- `sendTemporaryPasswordEmail(String toEmail, String temporaryPassword)`

Internal changes:
- Constructor injects `Resend` bean, `fromEmail`, and `frontendUrl`
- Private `doSend(String to, String subject, String htmlBody)` uses `CreateEmailOptions.builder()` and `resend.emails().send()`
- `sendHtmlEmail` throws `RuntimeException` on `ResendException`
- `sendHtmlEmailQuietly` logs errors without throwing
- Error handling semantics identical to current behavior

### 5. Test Changes

**`EmailServiceTest`:**
- Mock `Resend` instead of `JavaMailSender`
- Mock `resend.emails()` → mock `Emails` → mock `emails.send()`
- For failure tests, throw `ResendException`
- Same 6 test cases, same assertions

**`EmailPasswordAuthServiceTest` and `AdminServiceTest`:**
- No changes — they mock `EmailService` at the service level

### 6. CLAUDE.md Updates

- Environment Variables: remove SMTP vars, add `RESEND_API_KEY`
- Dependencies: replace `spring-boot-starter-mail` with `resend-java`
