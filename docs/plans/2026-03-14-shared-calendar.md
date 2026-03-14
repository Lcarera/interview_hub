# Shared Service Account Calendar Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace Google Calendar domain-wide delegation with direct service account calendar ownership, so calendar events work without a Google Workspace domain.

**Architecture:** The service account creates all events on its own calendar (configurable via `GOOGLE_CALENDAR_ID` env var). Interviewers, candidates, and shadowers are added as attendees and receive email invitations. All delegation logic, `calendarEmail` field, unused OAuth token fields, and `TokenEncryptionService` are removed.

**Tech Stack:** Spring Boot 4.0.2, Java 25, Google Calendar API v3, Google Auth Library, PostgreSQL (Supabase), MapStruct, JPA/Hibernate

---

### Task 1: Add `calendarId` to GoogleServiceAccountProperties

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

**Step 1: Add the `calendarId` property**

In `GoogleServiceAccountProperties.java`, add a field with a default:

```java
@Data
@Component
@ConfigurationProperties(prefix = "app.google-service-account")
public class GoogleServiceAccountProperties {
    private String keyJson;
    private String calendarId = "primary";
}
```

**Step 2: Add config binding in application.yml**

Under `app.google-service-account`, add:

```yaml
app:
  google-service-account:
    key-json: ${GOOGLE_SERVICE_ACCOUNT_KEY}
    calendar-id: ${GOOGLE_CALENDAR_ID:primary}
```

**Step 3: Add config binding in application-test.yml**

Add `calendar-id: primary` under the existing `google-service-account` section.

**Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat: add calendarId config property for shared calendar"
```

---

### Task 2: Rewrite GoogleCalendarService (no delegation)

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`

**Step 1: Write the new GoogleCalendarService**

Remove all `Profile` parameters from public methods. Remove `createDelegated()` and `getCalendarId(Profile)`. Use `serviceAccountProperties.getCalendarId()` everywhere. The `buildEvent` method now takes `Interview` only (interviewer email comes from `interview.getInterviewer().getEmail()`).

New method signatures:
- `public String createEvent(Interview interview) throws IOException`
- `public void updateEvent(Interview interview) throws IOException`
- `public void deleteEvent(String googleEventId) throws IOException`
- `public void addAttendee(String googleEventId, String attendeeEmail) throws IOException`

In `buildCalendarClient()` — remove the `Profile` parameter, remove `createDelegated()`:

```java
Calendar buildCalendarClient() throws IOException {
    if (serviceAccountProperties.getKeyJson() == null || serviceAccountProperties.getKeyJson().isBlank()) {
        throw new IOException("Google service account key not configured");
    }

    GoogleCredentials credentials = ServiceAccountCredentials
            .fromStream(new java.io.ByteArrayInputStream(serviceAccountProperties.getKeyJson().getBytes()))
            .createScoped(java.util.List.of("https://www.googleapis.com/auth/calendar"));

    credentials.refreshIfExpired();

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
```

In `createEvent(Interview interview)`:

```java
public String createEvent(Interview interview) throws IOException {
    Calendar calendar = buildCalendarClient();
    String calendarId = serviceAccountProperties.getCalendarId();
    Event event = buildEvent(interview);

    Event created = calendar.events().insert(calendarId, event)
            .setConferenceDataVersion(1)
            .setSendUpdates("all")
            .execute();
    log.debug("Created Google Calendar event: {}", created.getId());
    return created.getId();
}
```

Apply similar pattern to `updateEvent`, `deleteEvent`, `addAttendee` — remove `Profile` param, use `serviceAccountProperties.getCalendarId()`.

In `buildEvent(Interview interview)` — remove `Profile` param, get interviewer email from `interview.getInterviewer().getEmail()`:

```java
private Event buildEvent(Interview interview) {
    // ... same event building logic ...
    List<EventAttendee> attendees = new ArrayList<>();
    attendees.add(new EventAttendee().setEmail(interview.getInterviewer().getEmail()));

    Candidate candidate = interview.getCandidate();
    if (candidate != null && candidate.getEmail() != null) {
        attendees.add(new EventAttendee().setEmail(candidate.getEmail()));
    }
    event.setAttendees(attendees);
    return event;
}
```

Remove the `Profile` import if no longer used.

**Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: Compilation errors in InterviewService, ShadowingRequestService, and tests (they still pass Profile). This is expected — we fix them in the next tasks.

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java
git commit -m "refactor: remove delegation from GoogleCalendarService, use shared calendar"
```

---

### Task 3: Update InterviewService call sites

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/InterviewService.java`

**Step 1: Update createInterview**

Change line 63 from:
```java
String googleEventId = googleCalendarService.createEvent(interviewer, interview);
```
to:
```java
String googleEventId = googleCalendarService.createEvent(interview);
```

**Step 2: Update updateInterview**

Change line 109 from:
```java
googleCalendarService.updateEvent(interview.getInterviewer(), interview);
```
to:
```java
googleCalendarService.updateEvent(interview);
```

**Step 3: Update deleteInterview**

Change line 129 from:
```java
googleCalendarService.deleteEvent(interview.getInterviewer(), interview.getGoogleEventId());
```
to:
```java
googleCalendarService.deleteEvent(interview.getGoogleEventId());
```

**Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/InterviewService.java
git commit -m "refactor: update InterviewService to use simplified calendar API"
```

---

### Task 4: Update ShadowingRequestService call site

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java`

**Step 1: Update approveShadowingRequest**

Change lines 74-76 from:
```java
googleCalendarService.addAttendee(
        interview.getInterviewer(),
        interview.getGoogleEventId(),
        request.getShadower().getEmail());
```
to:
```java
googleCalendarService.addAttendee(
        interview.getGoogleEventId(),
        request.getShadower().getEmail());
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: SUCCESS (all production code now compiles)

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java
git commit -m "refactor: update ShadowingRequestService to use simplified calendar API"
```

---

### Task 5: Remove `calendarEmail` and token fields from Profile entity

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/domain/Profile.java`

**Step 1: Remove fields from Profile**

Remove these fields:
- `calendarEmail` (line 31-32)
- `googleAccessToken` (line 38-40)
- `googleRefreshToken` (line 42-44)
- `googleTokenExpiry` (line 46-48)

Remove the constructor that takes `calendarEmail`:
```java
public Profile(UUID id, String email, Role role, String calendarEmail) { ... }
```

Replace with a simpler constructor (or just remove it if only used in tests — check test usage first):
```java
public Profile(UUID id, String email, Role role) {
    this.id = id;
    this.email = email;
    this.role = role;
}
```

Remove the `Instant` import if no longer needed.

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/domain/Profile.java
git commit -m "refactor: remove calendarEmail and unused token fields from Profile"
```

---

### Task 6: Remove `calendarEmail` from ProfileDto

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/dto/ProfileDto.java`

**Step 1: Remove the field**

Remove:
```java
@Schema(description = "Google Calendar email (may differ from login email)")
String calendarEmail;
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/dto/ProfileDto.java
git commit -m "refactor: remove calendarEmail from ProfileDto"
```

---

### Task 7: Update ProfileMapper

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/mapper/ProfileMapper.java`

**Step 1: Remove calendarEmail and token mappings**

In `toProfileFromCreateUserRequest`: remove `@Mapping(target = "calendarEmail", ...)`, `@Mapping(target = "googleAccessToken", ...)`, `@Mapping(target = "googleRefreshToken", ...)`, `@Mapping(target = "googleTokenExpiry", ...)`.

In `toProfileFromRegisterRequest`: same — remove all mappings for the deleted fields.

In `toDto`: remove any `calendarEmail` mapping if present.

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/mapper/ProfileMapper.java
git commit -m "refactor: remove deleted field mappings from ProfileMapper"
```

---

### Task 8: Update AuthService

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/AuthService.java`

**Step 1: Remove calendarEmail assignment**

In `handleCallback()`, remove line ~104:
```java
profile.setCalendarEmail(email);
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/AuthService.java
git commit -m "refactor: remove calendarEmail from AuthService OAuth flow"
```

---

### Task 9: Delete TokenEncryptionService

**Files:**
- Delete: `src/main/java/com/gm2dev/interview_hub/service/TokenEncryptionService.java`

**Step 1: Search for usages**

Search the codebase for any remaining references to `TokenEncryptionService`. If any exist, remove those references.

**Step 2: Delete the file**

```bash
rm src/main/java/com/gm2dev/interview_hub/service/TokenEncryptionService.java
```

**Step 3: Remove `TOKEN_ENCRYPTION_KEY` from configuration**

In `application.yml`: remove the `app.token-encryption-key` line.
In `compose.yaml`: remove the `TOKEN_ENCRYPTION_KEY` env var.

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove TokenEncryptionService and TOKEN_ENCRYPTION_KEY"
```

---

### Task 10: Database migration — drop removed columns

**IMPORTANT: Use the `/create-migration` skill for this step.**

**Migration SQL:**

```sql
ALTER TABLE profiles DROP COLUMN IF EXISTS calendar_email;
ALTER TABLE profiles DROP COLUMN IF EXISTS google_access_token;
ALTER TABLE profiles DROP COLUMN IF EXISTS google_refresh_token;
ALTER TABLE profiles DROP COLUMN IF EXISTS google_token_expiry;
```

**Migration name:** `007_drop_calendar_and_token_columns.sql`

**Step 1: Create migration using `/create-migration` skill**

**Step 2: Commit**

```bash
git add supabase/migrations/
git commit -m "migration: drop calendar_email and token columns from profiles"
```

---

### Task 11: Update GoogleCalendarServiceTest

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java`

**Step 1: Update all test methods**

- Remove all `Profile` setup from calendar method calls
- Remove tests for `calendarEmail` resolution (`createEvent_usesCalendarEmail_whenSet`, `deleteEvent_usesCalendarEmail_whenSet`)
- Update `buildCalendarClient` spy calls — no longer takes a `Profile` parameter
- Add a test verifying the configured `calendarId` is used (mock `serviceAccountProperties.getCalendarId()` to return `"test-calendar-id"`)
- Update attendee assertions — interviewer email should come from `interview.getInterviewer().getEmail()`

**Step 2: Run tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.GoogleCalendarServiceTest`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java
git commit -m "test: update GoogleCalendarServiceTest for shared calendar"
```

---

### Task 12: Update InterviewServiceTest

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java`

**Step 1: Update mock verifications**

Change all `verify(googleCalendarService).createEvent(any(Profile.class), any(Interview.class))` to `verify(googleCalendarService).createEvent(any(Interview.class))`.

Same for `updateEvent` and `deleteEvent` — remove the `Profile` argument from verifications and `when()` stubs.

**Step 2: Update Profile construction**

If tests use the `Profile(UUID, String, Role, String)` constructor (with calendarEmail), update to use the new constructor or setter pattern.

**Step 3: Run tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.InterviewServiceTest`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java
git commit -m "test: update InterviewServiceTest for simplified calendar API"
```

---

### Task 13: Update ShadowingRequestServiceTest

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java`

**Step 1: Update mock verification**

Change `verify(googleCalendarService).addAttendee(any(Profile.class), eq(eventId), eq(email))` to `verify(googleCalendarService).addAttendee(eq(eventId), eq(email))`.

**Step 2: Update Profile construction if needed**

Same as Task 12 — remove calendarEmail from constructors.

**Step 3: Run tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.ShadowingRequestServiceTest`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java
git commit -m "test: update ShadowingRequestServiceTest for simplified calendar API"
```

---

### Task 14: Update remaining tests (Profile-related)

**Files:**
- Search and modify any remaining test files that reference `calendarEmail`, `googleAccessToken`, `googleRefreshToken`, `googleTokenExpiry`, or `TokenEncryptionService`

**Step 1: Search for remaining references**

```bash
grep -r "calendarEmail\|googleAccessToken\|googleRefreshToken\|googleTokenExpiry\|TokenEncryptionService\|token-encryption-key\|TOKEN_ENCRYPTION_KEY" src/test/ --include="*.java" --include="*.yml" -l
```

Fix all found references.

**Step 2: Run full test suite**

Run: `./gradlew test`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add -A
git commit -m "test: clean up remaining references to removed fields"
```

---

### Task 15: Update compose.yaml and CLAUDE.md

**Files:**
- Modify: `compose.yaml` — add `GOOGLE_CALENDAR_ID` env var, remove `TOKEN_ENCRYPTION_KEY`
- Modify: `CLAUDE.md` — update Environment Variables section (add `GOOGLE_CALENDAR_ID`, remove `TOKEN_ENCRYPTION_KEY`), update Google Calendar Integration section, remove references to `calendarEmail` and token encryption

**Step 1: Update compose.yaml**

Add to environment section:
```yaml
GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
```

Remove:
```yaml
TOKEN_ENCRYPTION_KEY: ${TOKEN_ENCRYPTION_KEY}
```

**Step 2: Update CLAUDE.md**

- In **Environment Variables**: add `GOOGLE_CALENDAR_ID` description, remove `TOKEN_ENCRYPTION_KEY`
- In **Google Calendar Integration**: describe the shared calendar model (service account owns all events, attendees get invitations)
- In **Key Technical Details > Authentication**: remove mention of AES-encrypted tokens
- In **Database Schema Management**: mention the new migration

**Step 3: Commit**

```bash
git add compose.yaml CLAUDE.md
git commit -m "docs: update compose and CLAUDE.md for shared calendar model"
```

---

### Task 16: Final verification

**Step 1: Clean build**

Run: `./gradlew clean test --no-build-cache`
Expected: ALL PASS, 95%+ branch coverage

**Step 2: Docker image build**

Run: `./gradlew bootBuildImage`
Expected: SUCCESS

**Step 3: Review all changes**

Run: `git log --oneline` to verify commit history is clean and logical.
