# Google Calendar User OAuth — Design

> **Issue:** #55 — Google Calendar 403 persists after sendUpdates=none fix

**Goal:** Replace the Google service account with a real Google account (`lcarera04@gmail.com`) for calendar operations, fixing the 403 attendee restriction and letting Google handle participant notifications.

---

## Root Cause

Google Calendar API rejects service accounts from adding attendees without Domain-Wide Delegation:

```
"message": "Service accounts cannot invite attendees without Domain-Wide Delegation of Authority."
"reason": "forbiddenForServiceAccounts"
```

The `sendUpdates=none` fix (PR #50) only controls email notifications — it does not bypass the attendee restriction.

---

## Authentication Change

**Current:** `GoogleCalendarService` authenticates via a service account JSON key (`GOOGLE_SERVICE_ACCOUNT_KEY`) using `GoogleCredentials.fromStream()`.

**New:** Authenticate as `lcarera04@gmail.com` using OAuth2 user credentials:
- Reuse existing `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
- Add new env var `GOOGLE_CALENDAR_REFRESH_TOKEN` (obtained via one-time CLI script)
- Replace `GoogleCredentials` with `UserCredentials` from `google-auth-library-oauth2-http`
- The refresh token auto-refreshes access tokens — no manual intervention needed unless revoked

**Calendar target:** Create a new dedicated calendar under `lcarera04@gmail.com`. Set `GOOGLE_CALENDAR_ID` to the new calendar's ID.

---

## Notification Strategy

**Current:** `sendUpdates="none"` + Resend sends custom HTML emails for invite/update/cancel/shadowing-approved.

**New:**
- Switch `setSendUpdates("none")` back to `setSendUpdates("all")` — Google sends proper calendar invitations with Meet links
- Remove from `EmailService`: `sendInterviewInviteEmail`, `sendInterviewUpdateEmail`, `sendInterviewCancellationEmail`, `sendShadowingApprovedEmail`
- Remove from `InterviewService`: `sendInviteEmails()`, `sendUpdateEmails()`, `sendCancellationEmails()` and their calls
- Remove from `ShadowingRequestService`: email call in `approveShadowingRequest()`
- Keep in `EmailService`: `sendVerificationEmail`, `sendPasswordResetEmail`, `sendTemporaryPasswordEmail` (auth-only)

---

## Config & Env Var Changes

**Remove:**
- `GOOGLE_SERVICE_ACCOUNT_KEY` env var
- `GoogleServiceAccountProperties` config class

**Add:**
- `GOOGLE_CALENDAR_REFRESH_TOKEN` env var

**Reuse:**
- `GOOGLE_CLIENT_ID` (already exists)
- `GOOGLE_CLIENT_SECRET` (already exists)
- `GOOGLE_CALENDAR_ID` (already exists, will point to new dedicated calendar)

**New config class:** `GoogleCalendarProperties` reading from `app.google-calendar.*`:
- `refreshToken` ← `GOOGLE_CALENDAR_REFRESH_TOKEN`
- `calendarId` ← `GOOGLE_CALENDAR_ID`
- Reuse `clientId` and `clientSecret` from existing `GoogleOAuthProperties`

---

## CLI Script for Token Bootstrapping

Standalone Python script (`scripts/get_calendar_token.py`):
- Reads `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from env or prompts for them
- Starts a local HTTP server on `http://localhost:8085`
- Opens browser to Google OAuth consent with scope `https://www.googleapis.com/auth/calendar` and `access_type=offline`
- User logs in as `lcarera04@gmail.com`, grants calendar access
- Script captures the auth code, exchanges for tokens, prints the refresh token
- One-time tool, not part of the application runtime

---

## Scope Summary

**What changes:**
1. `GoogleCalendarService` — replace service account auth with `UserCredentials`, switch `sendUpdates` to `"all"`
2. `GoogleServiceAccountProperties` → `GoogleCalendarProperties` (new config class)
3. `application.yml` — update config section
4. `InterviewService` — remove email-sending helpers and calls, remove `EmailService` dependency
5. `ShadowingRequestService` — remove email call on shadowing approval, remove `EmailService` dependency
6. `EmailService` — remove 4 interview notification methods
7. All related tests updated
8. New `scripts/get_calendar_token.py` for one-time token bootstrapping

**What doesn't change:**
- Calendar event CRUD logic (create/update/delete/addAttendee) — same API calls, just different auth
- Auth flow (Google OAuth login, JWT issuance)
- Resend auth emails (verification, password reset, temporary password)
- Database schema — no migration needed

**Env var changes for deployment:**
- Remove: `GOOGLE_SERVICE_ACCOUNT_KEY`
- Add: `GOOGLE_CALENDAR_REFRESH_TOKEN`
