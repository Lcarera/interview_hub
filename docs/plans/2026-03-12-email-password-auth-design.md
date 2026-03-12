# Email/Password Authentication Design

**Goal:** Add email/password login alongside existing Google OAuth so users can access the app without a Google login.

**Date:** 2026-03-12

---

## Constraints & Decisions

- Email/password users are still restricted to `@gm2dev.com` emails
- Self-registration with email verification (click-link flow)
- Admin manual user creation (with admin UI)
- Password requirements: min 8 chars, must include uppercase, lowercase, and a number
- First admin seeded via migration: `luciano.carera@gm2dev.com`
- Forgot password flow included (sends reset link via email)
- Google Calendar migrated to domain-wide delegation so all users get calendar features

---

## Section 1: Authentication Architecture

Two auth providers, one JWT system. The existing HMAC-SHA256 JWT issuance stays the same — both Google OAuth and email/password login produce the same app-issued JWT. Downstream security (guards, interceptors, resource server) doesn't change.

**New components:**
- `password_hash` and `email_verified` columns on the `profiles` table
- A `verification_tokens` table for email verification and password reset links
- BCrypt for password hashing
- Spring's `JavaMailSender` for sending verification/reset emails

**New endpoints:**
- `POST /auth/register` — self-registration (validates @gm2dev.com, sends verification email)
- `GET /auth/verify?token=...` — email verification link handler
- `POST /auth/login` — email + password login (returns JWT)
- `POST /auth/forgot-password` — sends reset email
- `POST /auth/reset-password` — resets password with token

**Profile behavior:** A user who registers with email/password gets a Profile with `google_sub = null` and no Google tokens. If later they also log in with Google OAuth, the profiles merge by email.

---

## Section 2: Admin System

**Role model:** `"admin"` as a role value alongside `"interviewer"`. First admin (`luciano.carera@gm2dev.com`) seeded via database migration.

**Admin endpoints:**
- `GET /admin/users` — list all users (pageable)
- `POST /admin/users` — create a user (email + temporary password, sends email with credentials)
- `PUT /admin/users/{id}/role` — promote/demote users
- `DELETE /admin/users/{id}` — deactivate a user

**Security:** All `/admin/**` endpoints require `ROLE_admin`. The existing `JwtAuthenticationConverter` already extracts the role claim and prefixes with `ROLE_`.

**Admin UI:** Angular route `/admin/users` with Material table showing users, dialog to create new users and manage roles. Only visible to admin-role users (route guard + nav menu conditional).

---

## Section 3: Frontend Changes

**Login page redesign:** Tabbed or split layout:
- Email/password form — email, password, "Sign in" button, "Forgot password?" link
- Google OAuth button — same as current
- "Create account" link — navigates to registration page

**New pages:**
- `/register` — registration form (email, password, confirm password) with @gm2dev.com client-side validation
- `/auth/verify` — success/error after clicking verification link
- `/auth/reset-password?token=...` — new password form
- `/auth/forgot-password` — enter email to receive reset link
- `/admin/users` — admin user management page

**Navigation:** User menu in app header with admin link (visible to admins only) and logout.

---

## Section 4: Google Calendar — Domain-Wide Delegation

**Current:** Each user's individual Google OAuth tokens create/manage calendar events.

**New:** Google Cloud service account with domain-wide delegation impersonates individual @gm2dev.com users.

**Changes:**
- `GoogleCalendarService` switches from per-user OAuth credentials to service account with `setServiceAccountUser(email)` for impersonation
- `google_access_token`, `google_refresh_token`, `google_token_expiry` columns become unused (cleanup in later migration)
- New env var: `GOOGLE_SERVICE_ACCOUNT_KEY` (JSON key contents or file path)

**Benefit:** All users get calendar features automatically with zero additional setup.

---

## Phases

1. **Phase 1 — Email/password auth** (register, verify, login, forgot/reset password) + DB migrations
2. **Phase 2 — Admin system** (role model, admin endpoints, admin UI, seed admin user)
3. **Phase 3 — Frontend overhaul** (login page redesign, new pages for register/verify/reset)
4. **Phase 4 — Domain-wide delegation** (service account calendar migration)

Each phase is independently deployable. Phases 1-3 can ship without Phase 4 (email/password users won't have calendar events until Phase 4).
