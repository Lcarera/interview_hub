# Google Calendar OAuth2 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken service account authentication in Google Calendar with OAuth2 user credentials, and fix two related gaps: approved shadowers being dropped on interview update (#73), and approved shadowing requests not removing the shadower from calendar on cancel/reject (#74).

**Architecture:** `GoogleCalendarService` is updated in-place — its `buildCalendarClient()` method swaps `ServiceAccountCredentials` for `UserCredentials` (same google-auth-library already in the classpath). A new `GoogleCalendarProperties` config class replaces `GoogleServiceAccountProperties` and is nested under the existing `app.google.*` YAML prefix so `clientId`/`clientSecret` are reused from `GoogleOAuthProperties`. The `buildEvent` method is extended to include approved shadowers from `interview.getShadowingRequests()`. `ShadowingRequestService.cancelShadowingRequest` and `rejectShadowingRequest` are updated to allow APPROVED → CANCELLED/REJECTED transitions and call `removeAttendee` when appropriate.

**Tech Stack:** Spring Boot 4 / Java 25, `com.google.auth.oauth2.UserCredentials` (already in `google-auth-library-oauth2-http`), Bun (for the bootstrap script), Mockito + JUnit 5, H2 for integration tests.

---

## Closes
- #71 — Bootstrap script for OAuth2 calendar refresh token
- #72 — Replace service account auth with user OAuth2 credentials
- #73 — Preserve approved shadowers as attendees on interview update
- #74 — Remove shadower from calendar on cancel/reject of approved request

## Out of scope
- #67 — CalendarSyncService architectural refactor (explicitly superseded by this PRD)
- Reconciliation cron for existing interviews without calendar events
- Custom branded email notifications for interview events

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Create | `scripts/get-calendar-token.ts` | Bun script — browser OAuth flow → prints refresh token |
| Create | `src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java` | `@ConfigurationProperties(prefix="app.google.calendar")` — holds `id` + `refreshToken` |
| Delete | `src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java` | Replaced by `GoogleCalendarProperties` |
| Modify | `src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java` | Auth swap, `sendUpdates="all"`, `buildEvent` includes approved shadowers, add `removeAttendee` |
| Modify | `src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java` | Relax status check, call `removeAttendee` on APPROVED→CANCELLED/REJECTED |
| Modify | `src/main/resources/application.yml` | Move calendar props under `app.google.calendar.*`, remove `google-service-account` |
| Modify | `compose.yaml` | Replace `GOOGLE_SERVICE_ACCOUNT_KEY` with `GOOGLE_CALENDAR_REFRESH_TOKEN` |
| Modify | `CLAUDE.md` | Update env vars section |
| Modify | `src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java` | Update constructor setup, fix stubs, add new tests |
| Modify | `src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java` | Update two existing tests, add four new tests |

---

## Task 1: Bootstrap script

**Files:**
- Create: `scripts/get-calendar-token.ts`

This is a self-contained Bun script with no Spring dependencies. No automated tests — it's a one-off interactive utility. Manual verification: run it, get a token.

- [ ] **Step 1: Create the script**

```typescript
// scripts/get-calendar-token.ts
//
// Obtains a Google OAuth2 refresh token for the calendar.events scope.
//
// Prerequisites:
//   1. Go to Google Cloud Console → APIs & Services → OAuth consent screen
//   2. Click "Publish App" — tokens from "Testing" mode expire after 7 days
//   3. Ensure GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set in .env or environment
//
// Usage:
//   bun run scripts/get-calendar-token.ts
//
// Copy the printed GOOGLE_CALENDAR_REFRESH_TOKEN= line into your .env file.

const CLIENT_ID = process.env.GOOGLE_CLIENT_ID;
const CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET;

if (!CLIENT_ID || !CLIENT_SECRET) {
  console.error("Error: GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be set in the environment or .env file.");
  process.exit(1);
}

const REDIRECT_PORT = 8888;
const REDIRECT_URI = `http://localhost:${REDIRECT_PORT}/callback`;
const SCOPE = "https://www.googleapis.com/auth/calendar.events";

const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
authUrl.searchParams.set("client_id", CLIENT_ID);
authUrl.searchParams.set("redirect_uri", REDIRECT_URI);
authUrl.searchParams.set("response_type", "code");
authUrl.searchParams.set("scope", SCOPE);
authUrl.searchParams.set("access_type", "offline");
authUrl.searchParams.set("prompt", "consent");

console.log("Opening browser to Google OAuth consent page...\n");

const { exec } = await import("child_process");
const openCmd =
  process.platform === "darwin" ? "open"
  : process.platform === "win32" ? "start"
  : "xdg-open";
exec(`${openCmd} "${authUrl.toString()}"`);

const code = await new Promise<string>((resolve) => {
  const server = Bun.serve({
    port: REDIRECT_PORT,
    fetch(req) {
      const url = new URL(req.url);
      if (url.pathname === "/callback") {
        const code = url.searchParams.get("code");
        if (code) {
          resolve(code);
          setTimeout(() => server.stop(), 100);
          return new Response("Authorization successful! You can close this tab.");
        }
      }
      return new Response("Waiting for authorization...");
    },
  });
  console.log(`Waiting for OAuth callback on port ${REDIRECT_PORT}...`);
});

const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({
    code,
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    redirect_uri: REDIRECT_URI,
    grant_type: "authorization_code",
  }),
});

const tokens = (await tokenResponse.json()) as {
  refresh_token?: string;
  error?: string;
  error_description?: string;
};

if (tokens.error || !tokens.refresh_token) {
  console.error("Error exchanging code for tokens:", tokens.error_description ?? tokens.error ?? "No refresh_token in response");
  process.exit(1);
}

console.log("\nSuccess! Add this to your .env file:\n");
console.log(`GOOGLE_CALENDAR_REFRESH_TOKEN=${tokens.refresh_token}\n`);
```

- [ ] **Step 2: Verify script runs**

```bash
cd /path/to/interview_hub
# Set credentials temporarily if not in .env
GOOGLE_CLIENT_ID=your_id GOOGLE_CLIENT_SECRET=your_secret bun run scripts/get-calendar-token.ts
```

Expected: browser opens to Google OAuth consent, you sign in with `lcarera04@gmail.com`, terminal prints `GOOGLE_CALENDAR_REFRESH_TOKEN=1//...`.

- [ ] **Step 3: Commit**

```bash
git add scripts/get-calendar-token.ts
git commit -m "feat(#71): add bootstrap script for OAuth2 calendar refresh token"
```

---

## Task 2: Config class swap

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java`
- Delete: `src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `compose.yaml`
- Modify: `CLAUDE.md`

The new class binds under `app.google.calendar.*`, which is a sub-prefix of the existing `app.google.*` that `GoogleOAuthProperties` owns. Spring Boot handles nested prefixes separately — each `@ConfigurationProperties` class is independent.

- [ ] **Step 1: Create `GoogleCalendarProperties`**

```java
// src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java
package com.gm2dev.interview_hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.google.calendar")
public class GoogleCalendarProperties {
    private String id = "primary";
    private String refreshToken;
}
```

- [ ] **Step 2: Update `application.yml`**

Replace the entire `google-service-account` block and update the `google` block:

Current state of the `app:` section (lines 27–51):
```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  cloud-tasks:
    ...
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${APP_BASE_URL:http://localhost:8080}/auth/google/callback
  jwt:
    ...
  mail:
    ...
  resend:
    ...
  google-service-account:
    key-json: ${GOOGLE_SERVICE_ACCOUNT_KEY:}
    calendar-id: ${GOOGLE_CALENDAR_ID:primary}
```

Replace to add `calendar` under `google` and remove `google-service-account`:
```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  cloud-tasks:
    project-id: ${GCP_PROJECT_ID:}
    location: ${GCP_LOCATION:us-central1}
    queue-id: ${CLOUD_TASKS_QUEUE_ID:email-queue}
    enabled: ${CLOUD_TASKS_ENABLED:false}
    service-account-email: ${CLOUD_TASKS_SA_EMAIL:}
    worker-url: ${CLOUD_TASKS_WORKER_URL:${APP_BASE_URL:http://localhost:8080}}
    audience: ${CLOUD_TASKS_AUDIENCE:${APP_BASE_URL:http://localhost:8080}}
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${APP_BASE_URL:http://localhost:8080}/auth/google/callback
    calendar:
      id: ${GOOGLE_CALENDAR_ID:primary}
      refresh-token: ${GOOGLE_CALENDAR_REFRESH_TOKEN:}
  jwt:
    signing-secret: ${JWT_SIGNING_SECRET}
    expiration-seconds: 3600
  mail:
    from: ${MAIL_FROM:noreply@lcarera.dev}
  resend:
    api-key: ${RESEND_API_KEY:}
```

- [ ] **Step 3: Update `compose.yaml`**

Find lines:
```yaml
      GOOGLE_SERVICE_ACCOUNT_KEY: ${GOOGLE_SERVICE_ACCOUNT_KEY}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
```

Replace with:
```yaml
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
```

- [ ] **Step 4: Update `CLAUDE.md` environment variables section**

Find:
```
- `GOOGLE_SERVICE_ACCOUNT_KEY` - Service account JSON key for calendar integration
- `GOOGLE_CALENDAR_ID` - Google Calendar ID for shared event calendar (default: `primary`)
```

Replace with:
```
- `GOOGLE_CALENDAR_REFRESH_TOKEN` - OAuth2 refresh token for Google Calendar access (obtained via `scripts/get-calendar-token.ts`)
- `GOOGLE_CALENDAR_ID` - Google Calendar ID for shared event calendar (default: `primary`)
```

Also find in the CI/CD secrets section:
```
- `GOOGLE_SERVICE_ACCOUNT_KEY` - Service account JSON key (same value as runtime, needed for calendar integration in deployed environments)
```
Remove that line entirely (the refresh token is already in the runtime env var, no separate CI secret needed for calendar).

- [ ] **Step 5: Delete `GoogleServiceAccountProperties.java`**

```bash
rm src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java
```

At this point the project will NOT compile — `GoogleCalendarService` still imports `GoogleServiceAccountProperties`. That is expected. We fix it in Task 4.

- [ ] **Step 6: Verify the project fails to compile as expected**

```bash
./gradlew compileJava 2>&1 | grep "cannot find symbol\|error:"
```

Expected: compile errors mentioning `GoogleServiceAccountProperties`. This confirms the old class is gone and the next task is needed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java
git add src/main/resources/application.yml
git add compose.yaml
git add CLAUDE.md
git rm src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java
git commit -m "chore(#72): replace GoogleServiceAccountProperties with GoogleCalendarProperties"
```

---

## Task 3: Update `GoogleCalendarServiceTest` (write failing tests first)

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java`

We update the test before fixing the service. The test file changes are:
1. **Constructor setup**: replace `GoogleServiceAccountProperties` with `GoogleOAuthProperties` + `GoogleCalendarProperties`
2. **All `setSendUpdates("none")` stubs**: change to `"all"` (the test chain will NPE with "none" once the service is fixed, but matching stubs is also good hygiene)
3. **Add `verify` assertions** on `setSendUpdates("all")` in the three dedicated operation tests
4. **Add new test**: `buildCalendarClient_throwsWhenRefreshTokenBlank`
5. **Add new test**: `removeAttendee_removesEmailFromEvent`
6. **Add new test**: `removeAttendee_whenNullAttendees_setsEmptyList`
7. **Add new test**: `updateEvent_includesApprovedShadowerAsAttendee`

- [ ] **Step 1: Update `GoogleCalendarServiceTest` — full replacement**

Replace the entire file content:

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleCalendarProperties;
import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private Calendar calendarClient;

    @Mock
    private Calendar.Events events;

    @Mock
    private Calendar.Events.Insert insert;

    @Mock
    private Calendar.Events.Update update;

    @Mock
    private Calendar.Events.Delete deleteOp;

    @Mock
    private Calendar.Events.Get getOp;

    @Mock
    private Calendar.Events.Patch patchOp;

    private GoogleCalendarService googleCalendarService;
    private GoogleCalendarProperties calendarProperties;
    private GoogleOAuthProperties oAuthProperties;

    @BeforeEach
    void setUp() {
        calendarProperties = new GoogleCalendarProperties();
        calendarProperties.setId("test-calendar-id");
        calendarProperties.setRefreshToken("test-refresh-token");

        oAuthProperties = new GoogleOAuthProperties();
        oAuthProperties.setClientId("test-client-id");
        oAuthProperties.setClientSecret("test-client-secret");

        googleCalendarService = spy(new GoogleCalendarService(oAuthProperties, calendarProperties));
    }

    private Interview buildInterview() {
        Profile interviewer = new Profile();
        interviewer.setId(UUID.randomUUID());
        interviewer.setEmail("interviewer@gm2dev.com");
        interviewer.setRole(Role.interviewer);

        Interview interview = new Interview();
        interview.setId(UUID.randomUUID());
        interview.setInterviewer(interviewer);
        interview.setTechStack("Java");
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setName("Jane Doe");
        candidate.setEmail("jane@example.com");
        interview.setCandidate(candidate);
        interview.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        interview.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        interview.setStatus(InterviewStatus.SCHEDULED);
        return interview;
    }

    @Test
    void buildCalendarClient_throwsWhenRefreshTokenBlank() {
        calendarProperties.setRefreshToken("");
        GoogleCalendarService bareService = new GoogleCalendarService(oAuthProperties, calendarProperties);
        assertThrows(IOException.class, bareService::buildCalendarClient);
    }

    @Test
    void createEvent_returnsGoogleEventId() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("google-event-id-123");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        GoogleCalendarService.CalendarEventResult result = googleCalendarService.createEvent(interview);

        assertEquals("google-event-id-123", result.eventId());
        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_usesSendUpdatesAll() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-send-updates");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        verify(insert).setSendUpdates("all");
    }

    @Test
    void createEvent_usesConfiguredCalendarId() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-cal-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_buildsEventWithCorrectFields() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-789");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();

        assertTrue(event.getSummary().contains("Java"));
        assertTrue(event.getSummary().contains("Jane Doe"));
        assertNotNull(event.getStart());
        assertNotNull(event.getEnd());
    }

    @Test
    void updateEvent_updatesExistingEvent() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("existing-event-id");

        Event updatedEvent = new Event().setId("existing-event-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        verify(events).update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void updateEvent_usesSendUpdatesAll() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-update-sends");

        Event updatedEvent = new Event().setId("event-update-sends");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-update-sends"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        verify(update).setSendUpdates("all");
    }

    @Test
    void updateEvent_includesApprovedShadowerAsAttendee() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-with-shadower");

        Profile shadower = new Profile();
        shadower.setId(UUID.randomUUID());
        shadower.setEmail("shadower@gm2dev.com");
        shadower.setRole(Role.interviewer);

        ShadowingRequest approvedRequest = new ShadowingRequest();
        approvedRequest.setId(UUID.randomUUID());
        approvedRequest.setShadower(shadower);
        approvedRequest.setStatus(ShadowingRequestStatus.APPROVED);
        approvedRequest.setInterview(interview);
        interview.getShadowingRequests().add(approvedRequest);

        Event updatedEvent = new Event().setId("event-with-shadower");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-with-shadower"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).update(eq("test-calendar-id"), eq("event-with-shadower"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertTrue(attendees.stream().anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void updateEvent_doesNotIncludePendingShadowerAsAttendee() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-pending-shadow");

        Profile shadower = new Profile();
        shadower.setId(UUID.randomUUID());
        shadower.setEmail("pending@gm2dev.com");
        shadower.setRole(Role.interviewer);

        ShadowingRequest pendingRequest = new ShadowingRequest();
        pendingRequest.setId(UUID.randomUUID());
        pendingRequest.setShadower(shadower);
        pendingRequest.setStatus(ShadowingRequestStatus.PENDING);
        pendingRequest.setInterview(interview);
        interview.getShadowingRequests().add(pendingRequest);

        Event updatedEvent = new Event().setId("event-pending-shadow");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-pending-shadow"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).update(eq("test-calendar-id"), eq("event-pending-shadow"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertFalse(attendees.stream().anyMatch(a -> "pending@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void deleteEvent_deletesEvent() throws IOException {
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.delete("test-calendar-id", "event-to-delete")).thenReturn(deleteOp);
        when(deleteOp.setSendUpdates("all")).thenReturn(deleteOp);

        googleCalendarService.deleteEvent("event-to-delete");

        verify(events).delete("test-calendar-id", "event-to-delete");
        verify(deleteOp).setSendUpdates("all");
        verify(deleteOp).execute();
    }

    @Test
    void createEvent_withNullCandidate_usesUnknownInSummary() throws IOException {
        Interview interview = buildInterview();
        interview.setCandidate(null);

        Event createdEvent = new Event().setId("event-null-candidate");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_withCandidateNullName_usesUnknownInSummary() throws IOException {
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setEmail("test@example.com");
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-name");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_formatsDescriptionCleanly() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-desc");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        String desc = captor.getValue().getDescription();

        assertTrue(desc.contains("Tech Stack: Java"));
        assertTrue(desc.contains("Candidate Details:"));
        assertTrue(desc.contains("Name: Jane Doe"));
        assertTrue(desc.contains("Email: jane@example.com"));
        assertFalse(desc.contains("{"), "Description should not contain raw map format");
    }

    @Test
    void createEvent_includesInterviewerAndCandidateAsAttendees() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-attendees");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(2, attendees.size());
        assertTrue(attendees.stream().anyMatch(a -> "interviewer@gm2dev.com".equals(a.getEmail())));
        assertTrue(attendees.stream().anyMatch(a -> "jane@example.com".equals(a.getEmail())));
    }

    @Test
    void createEvent_onlyInterviewerAttendee_whenNoCandidateEmail() throws IOException {
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setName("Jane Doe");
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-candidate-email");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(1, attendees.size());
        assertEquals("interviewer@gm2dev.com", attendees.get(0).getEmail());
    }

    @Test
    void addAttendee_addsEmailToEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-with-attendees")
                .setAttendees(List.of());

        Event patchedEvent = new Event().setId("event-with-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-with-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-with-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee("event-with-attendees", "shadower@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-with-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertTrue(patched.getAttendees().stream()
                .anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void addAttendee_withNullAttendeesList_addsAttendee() throws IOException {
        Event existingEvent = new Event().setId("event-null-attendees");

        Event patchedEvent = new Event().setId("event-null-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-null-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-null-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee("event-null-attendees", "new@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-null-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertEquals(1, patched.getAttendees().size());
        assertEquals("new@gm2dev.com", patched.getAttendees().get(0).getEmail());
    }

    @Test
    void removeAttendee_removesEmailFromEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-to-patch")
                .setAttendees(List.of(
                        new EventAttendee().setEmail("keep@gm2dev.com"),
                        new EventAttendee().setEmail("remove@gm2dev.com")
                ));
        Event patchedEvent = new Event().setId("event-to-patch");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-to-patch")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-to-patch"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee("event-to-patch", "remove@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-to-patch"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertEquals(1, attendees.size());
        assertEquals("keep@gm2dev.com", attendees.get(0).getEmail());
        assertFalse(attendees.stream().anyMatch(a -> "remove@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void removeAttendee_whenNullAttendees_setsEmptyList() throws IOException {
        Event existingEvent = new Event().setId("event-no-attendees");

        Event patchedEvent = new Event().setId("event-no-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-no-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-no-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee("event-no-attendees", "ghost@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-no-attendees"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getAttendees().isEmpty());
    }

    @Test
    void createEvent_includesGoogleMeetConferenceData() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-meet");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertNotNull(event.getConferenceData());
        assertEquals("hangoutsMeet",
                event.getConferenceData().getCreateRequest().getConferenceSolutionKey().getType());
        assertNotNull(event.getConferenceData().getCreateRequest().getRequestId());
    }
}
```

- [ ] **Step 2: Run the tests — verify they fail**

```bash
./gradlew test --tests com.gm2dev.interview_hub.service.GoogleCalendarServiceTest --no-build-cache
```

Expected failures:
- Compile errors because `GoogleCalendarService` constructor signature still uses `GoogleServiceAccountProperties`
- Once Task 4 makes the service compile, these tests will fail on `setSendUpdates("none")` stubs not matching and on missing `removeAttendee` method

Do NOT fix these yet — that is Task 4.

---

## Task 4: Update `GoogleCalendarService`

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`

- [ ] **Step 1: Replace the entire file**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleCalendarProperties;
import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class GoogleCalendarService {

    public record CalendarEventResult(String eventId, String meetLink) {}

    private final GoogleOAuthProperties oAuthProperties;
    private final GoogleCalendarProperties calendarProperties;

    public GoogleCalendarService(GoogleOAuthProperties oAuthProperties,
                                  GoogleCalendarProperties calendarProperties) {
        this.oAuthProperties = oAuthProperties;
        this.calendarProperties = calendarProperties;
    }

    public CalendarEventResult createEvent(Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(interview);

        Event created = calendar.events().insert(calendarId, event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Created Google Calendar event: {}", created.getId());
        return new CalendarEventResult(created.getId(), created.getHangoutLink());
    }

    public void updateEvent(Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(interview);

        calendar.events().update(calendarId, interview.getGoogleEventId(), event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Updated Google Calendar event: {}", interview.getGoogleEventId());
    }

    public void deleteEvent(String googleEventId) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();

        calendar.events().delete(calendarId, googleEventId)
                .setSendUpdates("all")
                .execute();
        log.debug("Deleted Google Calendar event: {}", googleEventId);
    }

    public void addAttendee(String googleEventId, String attendeeEmail) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            attendees.addAll(event.getAttendees());
        }
        attendees.add(new EventAttendee().setEmail(attendeeEmail));

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Added attendee {} to event {}", attendeeEmail, googleEventId);
    }

    public void removeAttendee(String googleEventId, String attendeeEmail) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            event.getAttendees().stream()
                    .filter(a -> !attendeeEmail.equals(a.getEmail()))
                    .forEach(attendees::add);
        }

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Removed attendee {} from event {}", attendeeEmail, googleEventId);
    }

    Calendar buildCalendarClient() throws IOException {
        if (calendarProperties.getRefreshToken() == null || calendarProperties.getRefreshToken().isBlank()) {
            throw new IOException("Google Calendar refresh token not configured");
        }

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(oAuthProperties.getClientId())
                .setClientSecret(oAuthProperties.getClientSecret())
                .setRefreshToken(calendarProperties.getRefreshToken())
                .build();

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Failed to create HTTP transport", e);
        }

        return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Interview Hub")
                .build();
    }

    private Event buildEvent(Interview interview) {
        Event event = new Event();

        Candidate candidate = interview.getCandidate();
        String candidateName = candidate != null && candidate.getName() != null ? candidate.getName() : "Unknown";
        event.setSummary(interview.getTechStack() + " Interview - " + candidateName);

        StringBuilder description = new StringBuilder();
        description.append("Tech Stack: ").append(interview.getTechStack());

        if (candidate != null) {
            description.append("\n\nCandidate Details:");
            description.append("\n  Name: ").append(candidate.getName());
            if (candidate.getEmail() != null) {
                description.append("\n  Email: ").append(candidate.getEmail());
            }
            if (candidate.getLinkedinUrl() != null) {
                description.append("\n  LinkedIn: ").append(candidate.getLinkedinUrl());
            }
            if (candidate.getPrimaryArea() != null) {
                description.append("\n  Primary Area: ").append(candidate.getPrimaryArea());
            }
            if (candidate.getFeedbackLink() != null) {
                description.append("\n  Feedback Link: ").append(candidate.getFeedbackLink());
            }
        }
        event.setDescription(description.toString());

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getStartTime())));
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getEndTime())));

        event.setStart(start);
        event.setEnd(end);

        ConferenceSolutionKey solutionKey = new ConferenceSolutionKey().setType("hangoutsMeet");
        CreateConferenceRequest conferenceRequest = new CreateConferenceRequest()
                .setConferenceSolutionKey(solutionKey)
                .setRequestId(UUID.randomUUID().toString());
        event.setConferenceData(new ConferenceData().setCreateRequest(conferenceRequest));

        List<EventAttendee> attendees = new ArrayList<>();
        attendees.add(new EventAttendee().setEmail(interview.getInterviewer().getEmail()));

        if (candidate != null && candidate.getEmail() != null) {
            attendees.add(new EventAttendee().setEmail(candidate.getEmail()));
        }

        if (interview.getShadowingRequests() != null) {
            interview.getShadowingRequests().stream()
                    .filter(sr -> ShadowingRequestStatus.APPROVED.equals(sr.getStatus()))
                    .map(sr -> sr.getShadower().getEmail())
                    .filter(email -> email != null)
                    .map(email -> new EventAttendee().setEmail(email))
                    .forEach(attendees::add);
        }

        event.setAttendees(attendees);

        return event;
    }
}
```

- [ ] **Step 2: Run `GoogleCalendarServiceTest` — verify all pass**

```bash
./gradlew test --tests com.gm2dev.interview_hub.service.GoogleCalendarServiceTest --no-build-cache
```

Expected: all tests PASS. If any fail, the error message will tell you which assertion or stub is wrong — fix only the specific issue before continuing.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test --no-build-cache
```

Expected: all tests pass. If `InterviewServiceTest` or other tests fail because they still reference `GoogleServiceAccountProperties` somewhere, fix those compilation errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java
git add src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java
git commit -m "feat(#72,#73): swap calendar auth to UserCredentials, sendUpdates=all, include approved shadowers in events"
```

---

## Task 5: Update `ShadowingRequestServiceTest` (write failing tests first)

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java`

Two existing tests must be renamed and updated — they currently approve then cancel/reject and expect `IllegalStateException`, but after our service change APPROVED → CANCELLED/REJECTED will be valid. We rename them to cover the actual invalid state (CANCELLED → cancel again).

Four new tests cover the approved-request transitions and their calendar side-effects.

- [ ] **Step 1: Replace two existing tests and add four new ones**

In `ShadowingRequestServiceTest.java`, make the following changes:

**Replace** `cancelShadowingRequest_whenNotPending_throwsIllegalStateException` (lines 112–118) with:
```java
@Test
void cancelShadowingRequest_whenAlreadyCancelled_throwsIllegalStateException() {
    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId()); // now CANCELLED

    assertThrows(IllegalStateException.class,
            () -> shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId()));
}
```

**Replace** `rejectShadowingRequest_whenNotPending_throwsIllegalStateException` (lines 159–165) with:
```java
@Test
void rejectShadowingRequest_whenAlreadyRejected_throwsIllegalStateException() {
    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.rejectShadowingRequest(request.getId(), null, interviewer.getId()); // now REJECTED

    assertThrows(IllegalStateException.class,
            () -> shadowingRequestService.rejectShadowingRequest(request.getId(), "Too late", interviewer.getId()));
}
```

**Add** the following four tests anywhere in the class (before the closing `}`):
```java
@Test
void cancelShadowingRequest_whenApproved_setsStatusToCancelled() {
    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

    ShadowingRequest cancelled = shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId());

    assertEquals(ShadowingRequestStatus.CANCELLED, cancelled.getStatus());
}

@Test
void cancelShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee() throws Exception {
    interview.setGoogleEventId("gcal-cancel-approved");
    interview = interviewRepository.save(interview);

    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

    shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId());

    verify(googleCalendarService).removeAttendee("gcal-cancel-approved", "shadower@example.com");
}

@Test
void rejectShadowingRequest_whenApproved_setsStatusToRejected() {
    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

    ShadowingRequest rejected = shadowingRequestService.rejectShadowingRequest(request.getId(), "No longer needed", interviewer.getId());

    assertEquals(ShadowingRequestStatus.REJECTED, rejected.getStatus());
    assertEquals("No longer needed", rejected.getReason());
}

@Test
void rejectShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee() throws Exception {
    interview.setGoogleEventId("gcal-reject-approved");
    interview = interviewRepository.save(interview);

    ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
    shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

    shadowingRequestService.rejectShadowingRequest(request.getId(), "reason", interviewer.getId());

    verify(googleCalendarService).removeAttendee("gcal-reject-approved", "shadower@example.com");
}
```

- [ ] **Step 2: Run `ShadowingRequestServiceTest` — verify new tests fail**

```bash
./gradlew test --tests com.gm2dev.interview_hub.service.ShadowingRequestServiceTest --no-build-cache
```

Expected failures:
- `cancelShadowingRequest_whenApproved_setsStatusToCancelled` — FAIL: throws `IllegalStateException`
- `cancelShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee` — FAIL: throws `IllegalStateException`
- `rejectShadowingRequest_whenApproved_setsStatusToRejected` — FAIL: throws `IllegalStateException`
- `rejectShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee` — FAIL: throws `IllegalStateException`

The renamed existing tests (`*_whenAlreadyCancelled_*`, `*_whenAlreadyRejected_*`) should PASS since they test a state that still throws.

---

## Task 6: Update `ShadowingRequestService`

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java`

- [ ] **Step 1: Replace `cancelShadowingRequest`, `rejectShadowingRequest`, and `requirePendingStatus`**

Replace the three methods:

```java
@Transactional
public ShadowingRequest cancelShadowingRequest(UUID id, UUID requesterId) {
    ShadowingRequest request = findById(id);
    if (!request.getShadower().getId().equals(requesterId)) {
        throw new AccessDeniedException("Only the shadower can cancel this request");
    }
    requireActionableStatus(request);

    boolean wasApproved = request.getStatus() == ShadowingRequestStatus.APPROVED;
    request.setStatus(ShadowingRequestStatus.CANCELLED);
    ShadowingRequest saved = shadowingRequestRepository.save(request);

    if (wasApproved) {
        Interview interview = request.getInterview();
        if (interview.getGoogleEventId() != null) {
            try {
                googleCalendarService.removeAttendee(interview.getGoogleEventId(), request.getShadower().getEmail());
            } catch (Exception e) {
                log.warn("Failed to remove shadower {} from Calendar event {}: {}",
                        request.getShadower().getEmail(), interview.getGoogleEventId(), e.getMessage());
            }
        }
    }

    return saved;
}

@Transactional
public ShadowingRequest rejectShadowingRequest(UUID id, String reason, UUID requesterId) {
    ShadowingRequest request = findById(id);
    if (!request.getInterview().getInterviewer().getId().equals(requesterId)) {
        throw new AccessDeniedException("Only the interviewer can reject this request");
    }
    requireActionableStatus(request);

    boolean wasApproved = request.getStatus() == ShadowingRequestStatus.APPROVED;
    request.setStatus(ShadowingRequestStatus.REJECTED);
    request.setReason(reason);
    ShadowingRequest saved = shadowingRequestRepository.save(request);

    if (wasApproved) {
        Interview interview = request.getInterview();
        if (interview.getGoogleEventId() != null) {
            try {
                googleCalendarService.removeAttendee(interview.getGoogleEventId(), request.getShadower().getEmail());
            } catch (Exception e) {
                log.warn("Failed to remove shadower {} from Calendar event {}: {}",
                        request.getShadower().getEmail(), interview.getGoogleEventId(), e.getMessage());
            }
        }
    }

    return saved;
}

private void requireActionableStatus(ShadowingRequest request) {
    if (request.getStatus() != ShadowingRequestStatus.PENDING
            && request.getStatus() != ShadowingRequestStatus.APPROVED) {
        throw new IllegalStateException(
                "Shadowing request cannot be modified. Current status: " + request.getStatus());
    }
}
```

Delete the old `requirePendingStatus` private method entirely.

- [ ] **Step 2: Run `ShadowingRequestServiceTest` — verify all pass**

```bash
./gradlew test --tests com.gm2dev.interview_hub.service.ShadowingRequestServiceTest --no-build-cache
```

Expected: all tests PASS.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test --no-build-cache
```

Expected: BUILD SUCCESSFUL, all tests pass, JaCoCo coverage check passes.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java
git add src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java
git commit -m "feat(#74): allow cancel/reject of approved shadowing requests, remove from calendar"
```

---

## Done

After all tasks are committed, open a PR targeting `main`. The PR closes issues #71, #72, #73, and #74.

```bash
git push origin <your-branch>
gh pr create \
  --title "feat: migrate Google Calendar to OAuth2 user credentials" \
  --body "Closes #71, #72, #73, #74"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|-------------|------|
| Bootstrap script prints refresh token | Task 1 |
| Replace `ServiceAccountCredentials` with `UserCredentials` | Task 4 |
| Remove `GOOGLE_SERVICE_ACCOUNT_KEY` env var | Task 2 |
| Add `GOOGLE_CALENDAR_REFRESH_TOKEN` env var | Tasks 2, 4 |
| `sendUpdates="all"` on all operations | Task 4 |
| Approved shadowers preserved on interview update | Task 4 (`buildEvent`) |
| Shadower removed from calendar on cancel of approved request | Task 6 |
| Shadower removed from calendar on reject of approved request | Task 6 |
| Calendar failures don't block DB operations | Preserved (existing try/catch pattern kept) |
| Unit tests for new `removeAttendee` | Task 3 |
| Unit tests for `sendUpdates="all"` | Task 3 |
| Integration tests for APPROVED cancel/reject | Task 5 |

**Placeholder scan:** None found.

**Type consistency:**
- `GoogleCalendarProperties.getId()` — used in `GoogleCalendarService` ✓
- `GoogleCalendarProperties.getRefreshToken()` — used in `buildCalendarClient()` ✓
- `GoogleOAuthProperties.getClientId()` / `.getClientSecret()` — existing getters ✓
- `googleCalendarService.removeAttendee(eventId, email)` — defined in Task 4, tested in Task 3, called in Task 6 ✓
- `ShadowingRequestStatus.APPROVED` — existing enum value ✓
- `requireActionableStatus` — defined and called in Task 6 ✓
