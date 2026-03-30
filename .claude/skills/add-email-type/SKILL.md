---
name: add-email-type
description: Step-by-step guide for adding a new email type to the Interview Hub email system. Use this skill whenever someone needs to add, create, or implement a new kind of email notification (e.g., interview reminder, cancellation notice, welcome email). Also use it when someone asks about extending the email/notification system or wants to know what files to touch for a new email type.
---

# Adding a New Email Type

This skill walks through every file that needs changes when adding a new email type to the async email system. After the self-rendering payload refactor, adding a new email type is a **2-step process** (down from 5 steps).

The email system uses **self-rendering sealed records** — each `EmailTaskPayload` record knows how to render its own subject and HTML body. No dispatcher changes, no controller switch statements.

## The two changes, in order

### 1. Add the self-rendering record to `EmailTaskPayload`

**File:** `src/main/java/com/gm2dev/interview_hub/dto/EmailTaskPayload.java`

Four things in this file:

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

**c) Define the record** inside the interface with the required `to()` field and type-specific fields:

```java
record NewEmailType(
        @NotBlank @Email String to,
        @NotBlank String someField
) implements EmailTaskPayload {
    @Override
    public String subject() {
        return "Interview Hub — Your Subject";
    }

    @Override
    public String htmlBody(EmailRenderContext ctx) {
        return "<h2>Your Heading</h2>"
                + "<p>Your email body here.</p>"
                + "<p>Field value: " + someField + "</p>";
    }

    // Optional: override propagateFailure() if this email should fail silently
    // @Override
    // public boolean propagateFailure() { return false; }
}
```

**d) Implement the required methods:**
- `subject()` — return the email subject line
- `htmlBody(EmailRenderContext ctx)` — return the HTML body (use `ctx.frontendUrl()` for links)
- `propagateFailure()` — defaults to `true` (throws on failure); override to `false` for silent failure

Use `@NotBlank` on required String fields so the worker rejects malformed payloads via `@Valid`.

### 2. Call `emailSender.send()` from business logic

Find the service where the trigger lives (e.g., `ShadowingRequestService`, `EmailPasswordAuthService`, `AdminService`) and call:

```java
emailSender.send(new EmailTaskPayload.NewEmailType(recipientEmail, someField));
```

Inject `EmailSender` into your service if not already present:

```java
private final EmailSender emailSender;

public YourService(EmailSender emailSender, /* other deps */) {
    this.emailSender = emailSender;
    // ...
}
```

### 3. Add tests

**a) Payload rendering test** — verify subject() and htmlBody() return expected content:

```java
@Test
void newEmailType_rendersCorrectSubjectAndBody() {
    var payload = new EmailTaskPayload.NewEmailType("user@gm2dev.com", "value");
    var ctx = new EmailRenderContext("http://localhost:4200");

    assertEquals("Interview Hub — Your Subject", payload.subject());
    assertTrue(payload.htmlBody(ctx).contains("value"));
}
```

**b) Caller integration test** — verify your service sends the correct payload:

```java
@MockitoBean
private EmailSender emailSender;

@Test
void yourMethod_sendsNewEmailType() {
    // ... trigger the action ...

    ArgumentCaptor<EmailTaskPayload> captor = ArgumentCaptor.forClass(EmailTaskPayload.class);
    verify(emailSender).send(captor.capture());

    EmailTaskPayload.NewEmailType email = (EmailTaskPayload.NewEmailType) captor.getValue();
    assertEquals("user@gm2dev.com", email.to());
    assertEquals("expectedValue", email.someField());
}
```

## What you DON'T need to change

After this refactor, these files no longer need modification when adding a new email type:

- `EmailService.java` — no new `queue*()` or `send*()` methods needed
- `InternalEmailController.java` — no switch case needed (polymorphism handles dispatch)

The sealed interface enforces exhaustiveness — the compiler will tell you if you forget to implement a required method.

## Checklist

- [ ] Record added to `EmailTaskPayload` (@JsonSubTypes + permits + record + subject() + htmlBody())
- [ ] Optional: override `propagateFailure()` if silent failure is desired
- [ ] Business logic calls `emailSender.send(new NewEmailType(...))`
- [ ] Payload rendering test added
- [ ] Caller integration test added
