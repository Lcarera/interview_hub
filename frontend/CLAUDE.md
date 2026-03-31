# CLAUDE.md — Frontend

Angular 21 SPA for Interview Hub. Uses Angular Material, standalone components, signal-based reactivity, and Bun as package manager.

## Commands

```bash
bun install                # install dependencies
bun start                  # dev server on localhost:4200
bun run build              # production build
bun run test               # vitest unit tests
```

## Tech Stack

- Angular 21.2 with Angular Material 21.2
- TypeScript 5.9 (strict mode, strict templates)
- Bun 1.2 (package manager + build runner)
- Vitest 4 with jsdom (unit testing)
- SCSS for styles
- Prettier (100 char width, single quotes, angular HTML parser)

## Project Structure

```
src/
├── main.ts                           # bootstrapApplication(App, appConfig)
├── index.html                        # Roboto + Material Icons from Google Fonts
├── styles.scss                       # Material theme (azure/blue, light mode)
├── environments/
│   ├── environment.ts                # dev: apiUrl = http://localhost:8080
│   └── environment.prod.ts           # prod: apiUrl = '' (same-origin via nginx)
└── app/
    ├── app.ts                        # Root component — just <router-outlet />
    ├── app.config.ts                 # Providers: router, httpClient + authInterceptor
    ├── app.routes.ts                 # Lazy-loaded routes with authGuard
    ├── core/
    │   ├── models/                   # TypeScript interfaces
    │   │   ├── profile.model.ts      # { id, email, role }
    │   │   ├── candidate.model.ts    # { id, name, email, linkedinUrl?, primaryArea?, feedbackLink? }
    │   │   ├── interview.model.ts    # { id, interviewer: Profile, candidate: Candidate, talentAcquisition?: Profile, ... }
    │   │   ├── dto.model.ts          # CreateInterviewRequest, UpdateInterviewRequest, RejectShadowingRequest
    │   │   └── shadowing-request.model.ts  # { id, interview?, shadower, status, reason? }
    │   ├── services/
    │   │   ├── auth.service.ts       # Token management, login/logout, email signal
    │   │   ├── interview.service.ts  # CRUD: list (paginated), get, create, update, remove
    │   │   ├── candidate.service.ts  # CRUD: list, get, create, update, remove
    │   │   ├── profile.service.ts    # list, getMe
    │   │   └── shadowing-request.service.ts  # CRUD: list, get, create, updateStatus
    │   ├── guards/
    │   │   └── auth.guard.ts         # Redirects to /login if not authenticated
    │   └── interceptors/
    │       └── auth.interceptor.ts   # Adds Authorization: Bearer header
    └── features/
        ├── auth/
        │   ├── login/                # Login page — "Sign in with Google" button
        │   │   ├── login.ts
        │   │   ├── login.html
        │   │   └── login.scss
        │   └── callback/
        │       └── auth-callback.ts  # Parses token from URL hash, stores in localStorage
        ├── candidates/
        │   ├── candidate-list/       # Table with create/edit/delete actions
        │   └── candidate-form-dialog/ # Create/edit candidate dialog
        └── home/
            └── home.ts               # Shows email, logout button (placeholder)
```

## Routing

All routes use lazy loading (`loadComponent`):

| Path | Component | Guard | Purpose |
|------|-----------|-------|---------|
| `/login` | LoginComponent | — | Google sign-in |
| `/auth/callback` | AuthCallbackComponent | — | Processes OAuth redirect |
| `/` (default) | DashboardComponent | authGuard | Main dashboard |
| `/interviews` | InterviewListComponent | authGuard | Paginated interview list |
| `/interviews/:id` | InterviewDetailComponent | authGuard | Interview detail + shadowing |
| `/candidates` | CandidateListComponent | authGuard | Candidate CRUD list |
| `/calendar` | CalendarComponent | authGuard | Embedded Google Calendar iframe |
| `/admin/users` | UserManagementComponent | authGuard + adminGuard | User role management |
| `**` | — | — | Redirects to `/` |

## Authentication Flow

1. `LoginComponent` calls `AuthService.login()` → redirects to backend `GET /auth/google`
2. Backend handles Google OAuth, redirects to `/auth/callback#token=...&email=...&expiresIn=...`
3. `AuthCallbackComponent` parses the hash fragment, calls `AuthService.handleCallback()`
4. Token, email, and expiry stored in localStorage (`ih_token`, `ih_email`, `ih_expires_at`)
5. All subsequent HTTP requests get `Authorization: Bearer <token>` via `authInterceptor`
6. `authGuard` checks `AuthService.isAuthenticated()` (token exists + not expired)

## Services API

**AuthService** — singleton, signal-based:
- `email: Signal<string | null>` — reactive email from localStorage
- `login()` — redirects to backend OAuth endpoint
- `handleCallback(token, email, expiresIn)` — stores auth data
- `getToken(): string | null` — retrieves JWT
- `isAuthenticated(): boolean` — checks token + expiry
- `logout()` — clears localStorage, resets signal

**InterviewService** — base URL: `${apiUrl}/api/interviews`:
- `list(page?, size?, sort?): Observable<Page<Interview>>` — paginated list
- `get(id): Observable<Interview>` — single interview
- `create(body: CreateInterviewRequest): Observable<Interview>` — POST (requires candidateId)
- `update(id, body: UpdateInterviewRequest): Observable<Interview>` — PUT
- `remove(id): Observable<void>` — DELETE

**CandidateService** — base URL: `${apiUrl}/api/candidates`:
- `list(): Observable<Candidate[]>` — all candidates
- `get(id): Observable<Candidate>` — single candidate
- `create(body: CandidateRequest): Observable<Candidate>` — POST
- `update(id, body: CandidateRequest): Observable<Candidate>` — PUT
- `remove(id): Observable<void>` — DELETE

**ProfileService** — base URL: `${apiUrl}/api/profiles`:
- `list(): Observable<Profile[]>` — all profiles
- `getMe(): Observable<Profile>` — current user's profile

**ShadowingRequestService** — base URL: `${apiUrl}/api/shadowing-requests`:
- `list(page?, size?): Observable<unknown>` — paginated list
- `get(id): Observable<ShadowingRequest>` — single request
- `create(body): Observable<ShadowingRequest>` — POST
- `updateStatus(id, status): Observable<ShadowingRequest>` — PATCH `/{id}/status`

## Architectural Patterns

- **Standalone components** — no NgModules, all components are `standalone: true`
- **OnPush change detection** — all components use `ChangeDetectionStrategy.OnPush`
- **Functional DI** — use `inject()` instead of constructor injection
- **Functional guards/interceptors** — `CanActivateFn` and `HttpInterceptorFn`
- **Signals for state** — `signal()` for reactive local state (e.g. `AuthService.email`)
- **Lazy-loaded routes** — all feature components loaded on demand
- **Separate template files** for components with non-trivial markup; inline for simple ones

## Styling

- Angular Material theme configured in `styles.scss` using `mat.theme()`
- Color: azure primary, blue tertiary, light mode
- Typography: Roboto (300, 400, 500 weights loaded from Google Fonts)
- Material Icons loaded from Google Fonts
- Component styles use SCSS (configured in `angular.json` via `inlineStyleLanguage`)

## Environment Configuration

| Variable | Dev | Prod |
|----------|-----|------|
| `apiUrl` | `http://localhost:8080` | `''` (empty — same-origin) |
| `production` | `false` | `true` |

In production, nginx proxies `/auth/*`, `/interviews`, `/shadowing-requests`, `/actuator` to the backend container. The empty `apiUrl` means all fetch calls are relative (same-origin).

In development, the Angular dev server (`bun start` on port 4200) calls the backend directly on port 8080.

## Docker Build

Multi-stage Dockerfile:
1. **Build stage** — `oven/bun:1.2-alpine`: installs deps, runs `bunx ng build --configuration production`
2. **Serve stage** — `nginx:1.27-alpine`: copies built assets to `/usr/share/nginx/html`, applies `nginx.conf`

The nginx config proxies API routes to `http://app:8080` (the backend Docker service) and serves all other paths as SPA fallback (`try_files $uri $uri/ /index.html`).

## Build Budgets

- Initial bundle: warn at 500kB, error at 1MB
- Component styles: warn at 4kB, error at 8kB

## Testing

- **Framework:** Vitest 4 with jsdom environment
- **Test files:** `**/*.spec.ts` (co-located with source)
- **Config:** `tsconfig.spec.json` extends base tsconfig, adds vitest globals
- **Pattern:** `TestBed.configureTestingModule()` with Angular testing utilities
- **Important:** Always run tests via `bun run test` (which uses `ng test`), NOT `npx vitest run` directly — vitest globals are only configured through the Angular test runner
- Current tests: root component instantiation and router outlet presence
