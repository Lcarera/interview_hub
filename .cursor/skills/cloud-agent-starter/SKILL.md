# Cloud Agent Starter — Interview Hub

Practical setup, testing, and workflow instructions for Cloud agents working on this codebase.

## Prerequisites

| Tool | Location | Verify |
|------|----------|--------|
| Java 25 | `/usr/lib/jvm/jdk-25.0.2+10` | `java -version` |
| Bun 1.2+ | `~/.bun/bin/bun` | `bun --version` |
| Gradle 9.3 | Auto-downloaded by `./gradlew` | First run takes ~10 s |

Source `~/.bashrc` if `java` or `bun` are not on `PATH`.

## Quick-Start Cheat Sheet

```
# Backend tests (H2, no secrets needed)
./gradlew test

# Backend build + coverage check (95 % branch min)
./gradlew check

# Frontend install + tests
cd frontend && bun install && bun run test

# Both in parallel (what CI runs)
./gradlew check & (cd frontend && bun install && bun run test) & wait
```

---

## 1 — Backend (Spring Boot / Java 25)

### 1.1 Running Tests

```bash
./gradlew test                        # all tests
./gradlew test --tests '*.CandidateServiceTest'   # one class
./gradlew test --tests '*.CandidateServiceTest.createCandidate_withValidRequest_returnsCandidate'  # one method
```

Tests use `@ActiveProfiles("test")` which activates `src/test/resources/application-test.yml`:
- H2 in-memory DB (`jdbc:h2:mem:testdb;MODE=PostgreSQL`, `ddl-auto: create-drop`)
- Dummy OAuth / JWT config — no real Google credentials needed
- `GoogleCalendarService` is always `@MockitoBean`'d in service tests

### 1.2 Test Patterns (never mix them)

**Controller tests** — slim, mock-based:
```
@WebMvcTest(XController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
```
- `@MockitoBean` for service layer, `JwtDecoder`, `JwtProperties`
- Authenticate with `.with(jwt())` from `spring-security-test`
- Assert HTTP status codes and JSON response shapes

**Service tests** — full Spring context with H2:
```
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
```
- Real repositories talk to H2
- `GoogleCalendarService` is `@MockitoBean`'d (it makes real HTTP calls)
- `AuthServiceTest` and `GoogleCalendarServiceTest` are pure Mockito (`@ExtendWith(MockitoExtension.class)`) — no Spring context

### 1.3 Coverage

JaCoCo enforces **95 % branch coverage**. Excluded from measurement:
- `InterviewHubApplication`, `GoogleCalendarService`, `OpenApiConfig`, `*MapperImpl`

Run `./gradlew check` to verify coverage. Reports land in `build/reports/jacoco/test/html/`.

If a new branch lowers coverage, add tests before committing.

### 1.4 Building the Full Backend

```bash
./gradlew build        # compile + test + coverage
./gradlew clean build  # from scratch
```

> **Do NOT use `./gradlew bootRun`** — the `spring-boot-docker-compose` devtools dependency tries to manage Docker and will fail in Cloud agent environments.

### 1.5 Running a Single Test and Reading Output

After `./gradlew test`, look for failures in:
- Terminal output (stack traces)
- `build/reports/tests/test/index.html` (HTML report)
- `build/test-results/test/` (XML results for CI)

---

## 2 — Frontend (Angular 21 / Bun / Vitest)

### 2.1 Install and Test

```bash
cd frontend
bun install          # warns about duplicate @angular/material — safe to ignore
bun run test         # Vitest with jsdom, exits after run
```

### 2.2 Test Patterns

Tests live next to source files as `*.spec.ts`. They use:
- `TestBed.configureTestingModule()` with the standalone component under test
- `provideHttpClient()` + `provideHttpClientTesting()` for HTTP mocking
- `provideAnimationsAsync()` for Material components
- `HttpTestingController` to intercept and flush requests

Example skeleton:
```ts
beforeEach(async () => {
  await TestBed.configureTestingModule({
    imports: [MyComponent],
    providers: [
      provideAnimationsAsync(),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  }).compileComponents();
  httpTesting = TestBed.inject(HttpTestingController);
});
afterEach(() => httpTesting.verify());
```

### 2.3 Dev Server (Manual UI Testing)

```bash
cd frontend && bun run start   # http://localhost:4200
```

The dev server calls the backend on port 8080 directly. Without a running backend, API calls will fail — but the UI will still render (useful for visual/component testing).

### 2.4 Production Build

```bash
cd frontend && bun run build
```

Budget limits: initial bundle warns at 500 kB, errors at 1 MB.

---

## 3 — Full-Stack (Docker Compose)

Only needed when testing end-to-end with a real backend. Requires external secrets.

```bash
# 1. Start Docker daemon (Cloud agent environments)
sudo dockerd &
sudo chmod 666 /var/run/docker.sock

# 2. Build backend image
./gradlew bootBuildImage

# 3. Create .env with all required secrets
#    DB_URL, DB_USERNAME, DB_PASSWORD, GOOGLE_CLIENT_ID,
#    GOOGLE_CLIENT_SECRET, JWT_SIGNING_SECRET, TOKEN_ENCRYPTION_KEY

# 4. Start
docker compose up -d

# 5. Verify
curl localhost:8080/actuator/health
```

Frontend runs on port 80 (nginx proxies `/api/*`, `/auth/*`, `/actuator` to backend).

> Full-stack testing requires a Supabase database and Google OAuth credentials. If secrets are not available, skip this and rely on backend + frontend unit tests.

---

## 4 — Authentication & Login

- Google OAuth restricted to `@gm2dev.com` accounts
- Login flow: `GET /auth/google` → Google consent → callback → JWT in URL hash
- In tests, authentication is mocked with `.with(jwt())` (backend) or by stubbing `AuthService` (frontend)
- No feature flags control auth — it is always on
- There is no admin role assignment flow; all users default to `role = "interviewer"`

**For Cloud agents**: you cannot log in to the real app without a `@gm2dev.com` Google account. For testing, always rely on the mocked auth in unit/integration tests.

---

## 5 — Database

- Production: PostgreSQL on Supabase (`ddl-auto: validate` — Hibernate will NOT create/modify schema)
- Tests: H2 in-memory (`ddl-auto: create-drop` — schema auto-created)
- Migrations: `supabase/migrations/001_*.sql` through `004_*.sql`

If you add or change an entity field:
1. Create a new migration in `supabase/migrations/` (see `.claude/skills/create-migration/SKILL.md`)
2. Update the entity class
3. Run `./gradlew test` to confirm H2 schema still works
4. The real Supabase schema must be updated separately (via `psql` or Supabase dashboard)

---

## 6 — CI / PR Checks

The CI pipeline (`.github/workflows/pr.yml`) runs two jobs in parallel:

| Job | Command | What it checks |
|-----|---------|----------------|
| `backend` | `./gradlew check` | Compile, tests, JaCoCo ≥ 95 % branch |
| `frontend` | `bun install && bun run test` | Vitest unit tests |

Before pushing, reproduce CI locally:
```bash
./gradlew check
cd frontend && bun install && bun run test
```

---

## 7 — Common Workflows

### Adding a new REST endpoint
1. Add method to the controller
2. Add/modify service logic
3. Add DTO + mapper if needed
4. Write a controller test (`@WebMvcTest`) and a service test (`@SpringBootTest`)
5. Run `./gradlew check` — fix any coverage drops

### Adding a new Angular component
1. Create component under `frontend/src/app/features/`
2. Add route in `app.routes.ts` (lazy-loaded)
3. Write a `*.spec.ts` co-located with the component
4. Run `cd frontend && bun run test`

### Adding a database column
1. Create migration SQL in `supabase/migrations/`
2. Update JPA entity
3. Update DTOs, mappers, services as needed
4. Run `./gradlew test` (H2 auto-creates schema from entities)

### Debugging a failing test
- Backend: check `build/reports/tests/test/index.html` for stack traces
- Frontend: Vitest output in terminal shows file, line, and diff
- For flaky H2 issues: ensure `@Transactional` + `@Rollback` are present on service tests

---

## 8 — Keeping This Skill Up to Date

Update this file whenever you discover:

- A new gotcha or environment quirk (e.g., dependency version conflicts, Docker socket issues)
- A useful testing shortcut (e.g., running a subset of tests, mocking a tricky dependency)
- Changes to the CI pipeline or coverage thresholds
- New feature flags, environment variables, or configuration that affects how tests run
- Patterns for testing new types of components (e.g., signal-based forms, new Material components)

To update: edit `.cursor/skills/cloud-agent-starter/SKILL.md` directly. Keep sections concise and example-driven. Prefer concrete commands over prose.
