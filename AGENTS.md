# AGENTS.md

## Cursor Cloud specific instructions

### System Dependencies

- **Java 25** (Eclipse Temurin 25.0.2) installed at `/usr/lib/jvm/jdk-25.0.2+10`; `JAVA_HOME` is set in `~/.bashrc`.
- **Bun 1.2+** installed at `~/.bun/bin/bun`; added to `PATH` in `~/.bashrc`.

### Services Overview

| Service | How to run | Notes |
|---------|-----------|-------|
| Backend tests | `./gradlew test` | Uses H2 in-memory DB; no external services needed. `GoogleCalendarService` is always mocked. |
| Backend full build | `./gradlew build` | Includes tests + JaCoCo coverage verification (95% branch min). |
| Frontend dev server | `cd frontend && bun run start` | Serves on port 4200; calls backend on `localhost:8080` in dev mode. |
| Frontend tests | `cd frontend && bun run test` | Vitest with jsdom; no backend required. |
| Full stack (Docker) | `docker compose up` | Requires `.env` file with all secrets (see `CLAUDE.md` Environment Variables section). |

### Running the Full Stack

To run the full stack via Docker Compose:
1. Build backend image: `./gradlew bootBuildImage` (requires Docker daemon running)
2. Create `.env` in project root with all required secrets (see `CLAUDE.md` Environment Variables)
3. `docker compose up -d` starts backend (port 8080) and frontend+nginx (port 80)
4. Health check: `curl $APP_BASE_URL/actuator/health` (default: port 8080)
5. Frontend at `http://localhost` proxies API calls through nginx to the backend

Docker must be started before building: `sudo dockerd &` (and `sudo chmod 666 /var/run/docker.sock` if needed for permissions).

### Key Gotchas

- **Do NOT use `./gradlew bootRun`** — the `spring-boot-docker-compose` devtools dependency will try to manage Docker and fail in this environment. Use `docker compose up` for running the full backend.
- **Database uses `ddl-auto: validate`** — Hibernate will NOT create or modify the schema. If migrations in `supabase/migrations/` haven't been applied to the target Supabase DB, the backend will fail with `SchemaManagementException`. Apply migrations manually via `psql`.
- Backend tests use `@ActiveProfiles("test")` which activates `application-test.yml` (H2 database, dummy OAuth/JWT config). No real database or Google credentials are needed for tests.
- The Gradle wrapper downloads Gradle 9.3.0 on first run (~10s). Subsequent builds are faster.
- Frontend has a duplicate `@angular/material` entry in `package.json` (dependencies + devDependencies); `bun install` warns but works fine.
- JaCoCo coverage threshold is 95% branch coverage (excludes `InterviewHubApplication`, `GoogleCalendarService`, and MapStruct-generated `*MapperImpl` classes).
- Running the full application requires external secrets (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SIGNING_SECRET`, `GOOGLE_SERVICE_ACCOUNT_KEY`) — see `CLAUDE.md` for the full list.
- Login requires a Google account with a domain in the `AllowedDomains.ALLOWED_DOMAINS` allowlist (`@gm2dev.com`, `@lcarera.dev`).

### Standard Commands

See `CLAUDE.md` (root) and `frontend/CLAUDE.md` for complete command references.
