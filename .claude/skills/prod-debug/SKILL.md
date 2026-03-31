---
name: prod-debug
description: Debug production issues in the Interview Hub backend running on Cloud Run. Use this skill whenever the user says something isn't working in production, reports a missing calendar event, email not sent, an API error, a 401/403/500 in prod, or asks to "check the logs" / "check Cloud Run" / "what's happening in prod". Also triggers when the user mentions a feature that works locally but not in production.
---

# Production Debugging — Interview Hub on Cloud Run

This skill guides you through fetching and interpreting Cloud Run logs for the Interview Hub backend.

## GCP Context

- **Project:** `interview-hub-prod`
- **Backend service:** `interview-hub-backend`
- **Frontend service:** `interview-hub-frontend`
- **Auth account to use:** `luciano.carera@gm2dev.com` (has project access)

## Step 1 — Verify gcloud is pointed at the right project

```bash
gcloud config get-value project
```

If it's not `interview-hub-prod`, set it:

```bash
gcloud config set project interview-hub-prod
```

## Step 2 — Fetch logs

### General recent errors (last 30 min)

```bash
gcloud logging read \
  "resource.type=cloud_run_revision \
   AND resource.labels.service_name=interview-hub-backend \
   AND severity>=WARNING" \
  --project=interview-hub-prod \
  --limit=50 \
  --format="table(timestamp,severity,textPayload,jsonPayload.message)" \
  --freshness=30m
```

### Filter by keyword (e.g. "calendar", "email", "401", a class name)

```bash
gcloud logging read \
  "resource.type=cloud_run_revision \
   AND resource.labels.service_name=interview-hub-backend \
   AND (textPayload=~\"KEYWORD\" OR jsonPayload.message=~\"KEYWORD\")" \
  --project=interview-hub-prod \
  --limit=50 \
  --format="table(timestamp,textPayload,jsonPayload.message)" \
  --freshness=1h
```

Replace `KEYWORD` with whatever is relevant (e.g. `calendar`, `email`, `401`, `GoogleCalendar`).

### Logs around a specific time (e.g. when the user says "I did X at 2pm")

```bash
gcloud logging read \
  "resource.type=cloud_run_revision \
   AND resource.labels.service_name=interview-hub-backend" \
  --project=interview-hub-prod \
  --limit=100 \
  --format="table(timestamp,severity,textPayload)" \
  --freshness=2h
```

Adjust `--freshness` as needed (e.g. `3h`, `12h`, `7d`).

## Step 3 — Interpret common errors

| Error | Likely cause | Fix |
|-------|-------------|-----|
| `401 Unauthorized` from Google Calendar API | `GOOGLE_CALENDAR_REFRESH_TOKEN` secret is stale/revoked | Re-run `scripts/get-calendar-token.ts`, update the secret, redeploy |
| `404 Not Found` from Google Calendar API | Wrong `GOOGLE_CALENDAR_ID` or the authorized account doesn't have write access to that calendar | Update `GOOGLE_CALENDAR_ID` env var in Cloud Run to the correct calendar ID |
| `403 Forbidden` from Google Calendar API | Insufficient OAuth scopes on the refresh token | Re-run token script to re-authorize with correct scopes |
| `Connection refused` / `JDBC` errors | DB_URL wrong or Supabase connection limit hit | Check Supabase dashboard; verify `DB_URL` secret |
| `JWT signature invalid` | `JWT_SIGNING_SECRET` mismatch between frontend token and backend | Verify the secret in Cloud Run matches what issued the token |
| Email not sent | `CLOUD_TASKS_ENABLED` misconfiguration or `RESEND_API_KEY` invalid | Check Cloud Tasks queue; verify Resend key is live |
| `NullPointerException` in service | Likely a missing env var that defaults to null | Check all required env vars are set in the Cloud Run service |

## Step 4 — Check or update secrets

List current secret versions:

```bash
gcloud secrets list --project=interview-hub-prod
gcloud secrets versions list GOOGLE_CALENDAR_REFRESH_TOKEN --project=interview-hub-prod
```

Add a new version:

```bash
echo -n "NEW_VALUE" | gcloud secrets versions add SECRET_NAME \
  --project=interview-hub-prod \
  --data-file=-
```

After updating a secret, **redeploy** so Cloud Run picks it up:

```bash
gcloud run services update interview-hub-backend \
  --project=interview-hub-prod \
  --region=us-central1
```

Or trigger a new deploy via GitHub Actions (push to main).

## Step 5 — View env vars currently set on the service

```bash
gcloud run services describe interview-hub-backend \
  --project=interview-hub-prod \
  --region=us-central1 \
  --format="yaml(spec.template.spec.containers[0].env)"
```

## Tips

- Calendar failures are **non-blocking** by design — the interview is saved even if the Calendar call fails. Look for `WARN` logs with `Failed to create/update Google Calendar event`.
- Email sending via Cloud Tasks is async — check both the Cloud Tasks queue and the `/internal/email-worker` endpoint logs.
- If `--freshness` doesn't go back far enough, use `--timestamp` with ISO 8601: `--timestamp="2026-03-31T10:00:00Z"`.
