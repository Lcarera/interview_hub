# Shared Service Account Calendar Design

## Problem

The app uses Google Service Account domain-wide delegation to impersonate users and create calendar events on their personal calendars. This requires a Google Workspace domain. The project's domains (`@gm2dev.com`, `@lcarera.dev`) are not Workspace domains, so delegation fails.

## Solution

Use the service account's own calendar directly (no delegation). All interview events are created on a single shared calendar. Attendees (interviewer, candidate, shadowers) receive invitations and see events on their personal calendars by accepting.

## Key Decisions

- **Single shared calendar** owned by the service account — no per-user calendars
- **Attendees added to all events** — interviewer, candidate, and shadowers get email invitations
- **Configurable calendar ID** via `GOOGLE_CALENDAR_ID` env var (default: `"primary"`) so a named "Interview Hub" calendar can be targeted
- **Domain allowlist stays hardcoded** in `AllowedDomains`
- **Clean break** — remove `calendarEmail`, unused OAuth token fields, `TokenEncryptionService`, and all delegation logic

## Changes

### 1. GoogleCalendarService

- Remove `createDelegated(email)` — use `ServiceAccountCredentials.createScoped()` directly
- New config property `app.google-service-account.calendar-id` (env var `GOOGLE_CALENDAR_ID`, default `"primary"`)
- All CRUD operations target the configured calendar ID
- Interviewer email added as attendee (since event is no longer on their calendar natively)
- `sendUpdates("all")` on create/update for notifications
- Method signatures simplified: no longer takes `Profile` for credentials

### 2. Profile Entity & Database

Remove fields:
- `calendarEmail`
- `googleAccessToken`, `googleRefreshToken`, `googleTokenExpiry` (unused)

Database migration (use `/create-migration` skill):
- Drop columns: `calendar_email`, `google_access_token`, `google_refresh_token`, `google_token_expiry` from `profiles`

Keep: `google_sub` (Google OAuth identity), `email`

### 3. Remove TokenEncryptionService

No longer needed — was encrypting/decrypting unused OAuth tokens.

### 4. Configuration

New in `application.yml`:
```yaml
app:
  google-service-account:
    calendar-id: ${GOOGLE_CALENDAR_ID:primary}
```

Update `compose.yaml` and `.env.example` to document `GOOGLE_CALENDAR_ID`.

### 5. InterviewService & ShadowingRequestService

Simplify call signatures:
- `createEvent(interview)` instead of `createEvent(profile, interview)`
- `updateEvent(interview)` instead of `updateEvent(profile, interview)`
- `deleteEvent(googleEventId)` instead of `deleteEvent(profile, googleEventId)`
- `addAttendee(googleEventId, email)` instead of `addAttendee(profile, googleEventId, email)`

### 6. Tests

- Update `GoogleCalendarServiceTest` — test direct creation on configured calendar ID, interviewer as attendee
- Update service test mocks to match new method signatures
- Remove `TokenEncryptionServiceTest`
- Update `ProfileMapper`/`ProfileDto` tests for dropped fields
- Controller tests unchanged (mock service layer)
