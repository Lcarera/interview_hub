---
name: add-email-type
description: Step-by-step guide for adding a new email type to the Interview Hub email system. Use this skill whenever someone needs to add, create, or implement a new kind of email notification (e.g., interview reminder, cancellation notice, welcome email). Also use it when someone asks about extending the email/notification system or wants to know what files to touch for a new email type.
---

# Adding a New Email Type

This skill walks through every file that needs changes when adding a new email type to the async email system (sealed interface + Google Cloud Tasks + Resend).

The email system uses a **sealed interface with records** for type-safe payloads, **pattern matching switch** in the worker controller, and a **queue-or-send facade** in EmailService. All five touchpoints must stay in sync.

## The five changes, in order

### 1. Add the record to `EmailTaskPayload`

**File:** `src/main/java/com/gm2dev/interview_hub/dto/EmailTaskPayload.java`

Three things in this file:

**a) Add to `@JsonSubTypes`** — pick an UPPER_SNAKE_CASE name for the `"type"` discriminator:

```java
@JsonSubTypes({
        // ... existing types ...
        @JsonSubTypes.Type(value = EmailTaskPayload.NewEmailType.class, name = "NEW_EMAIL_TYPE")
})
```

**b) Add to `permits` clause:**

```java
public sealed interface EmailTaskPayload permits
        // ... existing permits ...
        EmailTaskPayload.NewEmailType {
```

**c) Define the record** inside the interface. Every record must have `@NotBlank @Email String to()` as its first field (the sealed interface declares this contract). Add whatever extra fields the email template needs:

```java
record NewEmailType(
        @NotBlank @Email String to,
        @NotBlank String someField
) implements EmailTaskPayload {}
```

Use `@NotBlank` on required String fields so the worker rejects malformed payloads via `@Valid`.

### 2. Add a case to the worker switch

**File:** `src/main/java/com/gm2dev/interview_hub/controller/InternalEmailController.java`

Add a case to the pattern-matching `switch` in `processEmailTask()`:

```java
case EmailTaskPayload.NewEmailType e ->
        emailService.sendNewEmailType(e.to(), e.someField());
```

The compiler enforces exhaustiveness on sealed types — it won't compile until you add this case. That's the whole point of the sealed interface: you can't forget a case.

### 3. Add send + queue methods to `EmailService`

**File:** `src/main/java/com/gm2dev/interview_hub/service/EmailService.java`

**a) The send method** — builds the HTML and calls Resend:

```java
public void sendNewEmailType(String toEmail, String someField) {
    String subject = "Your Subject";
    String html = "...";  // build your email HTML
    sendEmail(toEmail, subject, html);
}
```

Follow the existing pattern: use string concatenation or a template for the HTML body, then delegate to the private `sendEmail()` helper.

**b) The queue facade method** — queues if Cloud Tasks is enabled, otherwise sends directly:

```java
public void queueNewEmailType(String toEmail, String someField) {
    if (isCloudTasksEnabled()) {
        emailQueueService.queueEmail(new EmailTaskPayload.NewEmailType(toEmail, someField));
    } else {
        sendNewEmailType(toEmail, someField);
    }
}
```

This is the method that other services call. The send method is only called directly by the worker controller.

### 4. Call the queue method from business logic

Find the service where the trigger lives (e.g., `ShadowingRequestService`, `EmailPasswordAuthService`, `AdminService`) and call:

```java
emailService.queueNewEmailType(recipientEmail, someField);
```

### 5. Add tests

**a) Controller test** — in `InternalEmailControllerTest`, add a test that POSTs the new payload type with the `X-CloudTasks-QueueName` header and verifies the correct `emailService.sendXxx()` is called:

```java
@Test
void processEmailTask_newEmailType_callsSendMethod() throws Exception {
    mockMvc.perform(post("/internal/email-worker")
                    .header("X-CloudTasks-QueueName", "email-queue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"type":"NEW_EMAIL_TYPE","to":"user@gm2dev.com","someField":"value"}
                            """)
                    .with(csrf()))
            .andExpect(status().isOk());

    verify(emailService).sendNewEmailType("user@gm2dev.com", "value");
}
```

**b) Queue service test** — in `EmailQueueServiceTest`, verify the payload serializes correctly and creates a Cloud Task.

**c) EmailService test** — test both the `queue` method (delegates to queue service or falls back to send) and the `send` method (builds correct HTML and calls Resend).

## Checklist

- [ ] Record added to `EmailTaskPayload` (JsonSubTypes + permits + record definition)
- [ ] Case added to `InternalEmailController` switch
- [ ] `sendXxx()` method added to `EmailService`
- [ ] `queueXxx()` facade method added to `EmailService`
- [ ] Business logic calls `queueXxx()`
- [ ] Controller test added
- [ ] Queue service test added
- [ ] EmailService test added
