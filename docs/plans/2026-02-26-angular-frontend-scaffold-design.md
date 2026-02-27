# Angular Frontend Scaffold Design

**Date:** 2026-02-26
**Status:** Approved

---

## Context

Interview Hub currently exposes a REST API consumed by Postman or external clients. To make the application usable by non-technical stakeholders (hiring managers, interviewers, and shadowers), we need a web UI. The chosen approach is an Angular 21 Single-Page Application (SPA) that communicates exclusively with the existing backend API. No backend logic will be duplicated in the frontend; the Angular app is a thin client over the REST layer.

---

## Architecture Overview

### Monorepo Layout

The Angular application lives inside the existing repository under a top-level `frontend/` directory. This keeps backend and frontend in one place for coordinated commits and shared CI.

```
interview_hub/
├── src/                          # Spring Boot backend (unchanged)
├── supabase/                     # Database migrations (unchanged)
├── frontend/                     # Angular 21 SPA (new)
│   ├── angular.json
│   ├── package.json
│   ├── tsconfig.json
│   ├── nginx.conf                # nginx config for production container
│   ├── Dockerfile                # Multi-stage build (build + nginx serve)
│   └── src/
│       ├── app/
│       │   ├── core/             # Singleton services, guards, interceptors
│       │   └── features/         # Feature modules (interviews, shadowing, auth)
│       ├── assets/
│       ├── environments/
│       │   ├── environment.ts
│       │   └── environment.prod.ts
│       ├── index.html
│       └── main.ts
├── compose.yaml                  # Updated to include frontend service
├── build.gradle
└── CLAUDE.md
```

### Technology Choices

| Concern | Choice | Reason |
|---|---|---|
| Framework | Angular 21 (standalone components) | Team standard; strong typing via TypeScript |
| UI components | Angular Material 21 | Consistent MD3 design; well-maintained |
| HTTP client | Angular `HttpClient` | Built-in; pairs with interceptors for auth headers |
| Routing | Angular Router | Built-in; supports lazy-loaded feature routes |
| State | Local component state + services | No complex global state needed at this scale |
| Build | Angular CLI (`ng build`) | Standard; outputs static assets for nginx |
| Container | nginx (alpine) | Lightweight static file server + reverse proxy |

---

## Authentication Flow

The backend already handles the Google OAuth exchange and issues its own HMAC-SHA256 JWTs. The frontend participates only in the redirect leg and the token storage step.

### Step-by-step

1. The user clicks **Sign in with Google** in the Angular app.
2. The Angular app navigates the browser to `GET /auth/google` on the backend. (Simple `window.location.href` redirect — no AJAX.)
3. The backend redirects the user to Google's consent screen.
4. Google redirects back to the backend at `GET /auth/google/callback?code=...`.
5. **Key backend change (see below):** instead of returning JSON, the callback handler redirects to the frontend callback route, embedding the JWT in the URL fragment:
   ```
   http://localhost:4200/auth/callback#token=<jwt>
   ```
6. The Angular `AuthCallbackComponent` (at route `/auth/callback`) reads the fragment, extracts the token, and stores it in `localStorage` under the key `access_token`.
7. The component then navigates to the application home route (`/interviews`).
8. A global `AuthInterceptor` attaches the stored token as an `Authorization: Bearer <token>` header on every outgoing `HttpClient` request.
9. If a 401 is received, the interceptor redirects to `/login`.

### Token Lifecycle

- The JWT is valid for 1 hour (matches the existing backend setting).
- No refresh token flow is implemented in this scaffold; users re-authenticate after expiry.
- On explicit logout the token is removed from `localStorage` and the user is redirected to `/login`.

### Fragment vs Query Parameter

The JWT is passed via the URL **fragment** (`#token=...`) rather than a query parameter. Fragments are not sent to the server in HTTP requests, so the token is never logged in nginx access logs or forwarded to third-party scripts via `Referer` headers.

---

## Project Structure Detail

```
frontend/src/app/
├── core/
│   ├── auth/
│   │   ├── auth.service.ts           # Token storage/retrieval, isAuthenticated()
│   │   ├── auth.interceptor.ts       # Attaches Bearer token to requests
│   │   └── auth.guard.ts             # CanActivate guard for protected routes
│   ├── models/
│   │   ├── interview.model.ts
│   │   ├── profile.model.ts
│   │   └── shadowing-request.model.ts
│   └── services/
│       ├── interview.service.ts      # HTTP calls to /api/interviews
│       └── shadowing-request.service.ts
├── features/
│   ├── auth/
│   │   ├── login/
│   │   │   └── login.component.ts    # "Sign in with Google" button
│   │   └── callback/
│   │       └── auth-callback.component.ts  # Reads fragment, stores token
│   ├── interviews/
│   │   ├── interviews.routes.ts      # Lazy-loaded route config
│   │   ├── interview-list/
│   │   │   └── interview-list.component.ts
│   │   └── interview-detail/
│   │       └── interview-detail.component.ts
│   └── shadowing/
│       ├── shadowing.routes.ts
│       └── shadowing-list/
│           └── shadowing-list.component.ts
├── app.component.ts                  # Shell with router-outlet + nav bar
├── app.config.ts                     # provideRouter, provideHttpClient, etc.
└── app.routes.ts                     # Top-level routes
```

### Route Map

| Path | Component | Guard |
|---|---|---|
| `/login` | `LoginComponent` | None (public) |
| `/auth/callback` | `AuthCallbackComponent` | None (public) |
| `/interviews` | `InterviewListComponent` | `AuthGuard` |
| `/interviews/:id` | `InterviewDetailComponent` | `AuthGuard` |
| `/shadowing` | `ShadowingListComponent` | `AuthGuard` |
| `**` (wildcard) | Redirect to `/interviews` | — |

---

## Docker and nginx

### Multi-Stage Dockerfile

```dockerfile
# Stage 1: build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: serve
FROM nginx:1.27-alpine
COPY --from=builder /app/dist/interview-hub-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### nginx Configuration

nginx serves the Angular static assets and reverse-proxies `/api/` and `/auth/` paths to the Spring Boot backend. This avoids CORS issues: from the browser's perspective, all traffic goes to the same origin.

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Reverse proxy: API calls
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Reverse proxy: Auth endpoints
    location /auth/ {
        proxy_pass http://backend:8080/auth/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # SPA fallback: all other paths serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### Updated compose.yaml Services

```yaml
services:
  backend:
    # existing backend service (unchanged)
    image: interview-hub:latest
    env_file: .env
    ports:
      - "8080:8080"

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      - backend
```

The frontend container is exposed on port 80. The backend container no longer needs to be directly accessible from the host (port 8080 can be dropped in production), since all browser traffic routes through nginx.

---

## Key Backend Change

### /auth/google/callback: JSON Response → Frontend Redirect

**Current behavior:** `GET /auth/google/callback` exchanges the Google authorization code, issues an app JWT, and returns a JSON response body with the token.

**New behavior:** After issuing the JWT, the controller redirects the browser to the Angular callback route with the token in the URL fragment:

```java
// AuthController.java
String frontendCallbackUrl = appBaseUrl
    .replace(":8080", "")          // frontend is on port 80
    + "/auth/callback#token=" + jwt;
return ResponseEntity.status(HttpStatus.FOUND)
    .header(HttpHeaders.LOCATION, frontendCallbackUrl)
    .build();
```

The `APP_BASE_URL` environment variable will be updated to point to the frontend origin (e.g., `http://localhost` in development, the production domain in prod).

The `POST /auth/token` Postman-compatible endpoint is **not changed** — it continues to return JSON, so existing Postman workflows remain functional.

---

## Out of Scope for This Scaffold

The following are explicitly deferred to subsequent tasks:

- JWT refresh / silent re-authentication
- Role-based UI hiding (interviewer vs. shadower views)
- Interview creation / edit forms
- Pagination controls in interview list
- End-to-end tests (Cypress or Playwright)
- Production domain configuration and TLS termination
