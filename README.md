# Interview Hub

A fullstack application for managing technical interviews and shadowing requests. Built with Spring Boot 4.0.2 (Java 25) and Angular 21, deployed on GCP Cloud Run with Cloudflare DNS.

## Architecture

```
  interview-hub.lcarera.dev          i-hub-be.lcarera.dev
           |                                  |
   +-------v--------+                +-------v--------+
   | Cloudflare DNS  |                | Cloudflare DNS |
   | (CNAME proxy)   |                | (CNAME proxy)  |
   +-------+---------+                +-------+--------+
           |                                  |
  +--------v-----------+          +-----------v----------+
  | GCP Cloud Run      |          | GCP Cloud Run        |
  | Frontend           |          | Backend              |
  | Angular + nginx :80|          | Spring Boot :8080    |
  +--------------------+          +-----------+----------+
                                              |
                                  +-----------v-----------+
                                  | Supabase              |
                                  | PostgreSQL            |
                                  +-----+-----------+-----+
                                        |           |
                                   Google       Google
                                   OAuth 2.0   Calendar
                                                API v3
```

## Tech Stack

| Layer          | Technology                                  |
|----------------|---------------------------------------------|
| Backend        | Spring Boot 4.0.2, Java 25, PostgreSQL      |
| Frontend       | Angular 21, Angular Material 21, TypeScript 5.9 |
| Infrastructure | Pulumi (Python), GCP Cloud Run              |
| DNS/CDN        | Cloudflare (DNS proxy)                      |
| Auth           | Google OAuth 2.0 (@gm2dev.com), HMAC-SHA256 JWT |
| CI/CD          | GitHub Actions                              |
| Database       | Supabase (PostgreSQL with JSONB)            |
| Package Mgr    | Gradle (backend), Bun 1.2 (frontend)       |

## Prerequisites

- Java 25 (Eclipse Temurin)
- Bun 1.2+
- Docker & Docker Compose
- Pulumi CLI (for infrastructure changes)
- Cloudflare account (for DNS management)

## Quick Start

1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd interview_hub
   ```

2. **Create a `.env` file** in the project root with required environment variables (see [Environment Variables](#environment-variables)).

3. **Build the backend Docker image:**
   ```bash
   ./gradlew bootBuildImage
   ```

4. **Start both services:**
   ```bash
   docker compose up
   ```

   This starts:
   - **Backend** on `http://localhost:8080`
   - **Frontend** on `http://localhost` (port 80)

5. **For frontend-only development** (assumes backend is running on port 8080):
   ```bash
   cd frontend
   bun install
   bun run start   # http://localhost:4200
   ```

## Environment Variables

| Variable               | Description                                    | Default                  |
|------------------------|------------------------------------------------|--------------------------|
| `DB_URL`               | PostgreSQL JDBC URL                            | -                        |
| `DB_USERNAME`          | Database username                              | -                        |
| `DB_PASSWORD`          | Database password                              | -                        |
| `GOOGLE_CLIENT_ID`     | Google OAuth 2.0 client ID                     | -                        |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 client secret                 | -                        |
| `JWT_SIGNING_SECRET`   | HMAC-SHA256 key for JWT signing (min 32 bytes) | -                        |
| `TOKEN_ENCRYPTION_KEY` | AES key for encrypting Google tokens at rest   | -                        |
| `APP_BASE_URL`         | Backend base URL for OAuth callbacks           | `http://localhost:8080`  |
| `FRONTEND_URL`         | Frontend URL for post-auth redirects           | `http://localhost`       |

## Project Structure

```
interview_hub/
├── src/                  # Spring Boot backend (Java 25)
├── frontend/             # Angular 21 SPA
├── infra/                # Pulumi IaC (GCP Cloud Run, Secret Manager, etc.)
├── supabase/migrations/  # PostgreSQL schema migrations
├── postman/              # Postman collection for API testing
├── .github/workflows/    # CI/CD pipeline (GitHub Actions)
├── compose.yaml          # Local Docker Compose (backend + frontend)
├── build.gradle          # Gradle build config
└── CLAUDE.md             # AI assistant instructions
```

See per-module documentation:
- [Backend (src/)](src/README.md)
- [Frontend](frontend/README.md)
- [Infrastructure](infra/README.md)

## CI/CD

The GitHub Actions workflow (`.github/workflows/deploy.yml`) triggers on every push to `main`:

1. Builds the backend image with `./gradlew bootBuildImage`
2. Builds the frontend image with Docker (multi-stage: Bun + nginx)
3. Pushes both images to GCP Artifact Registry (tagged with git SHA)
4. Deploys to GCP Cloud Run via `pulumi up`

**Required GitHub Secrets:**
- `GCP_SA_KEY` — GCP service account JSON key
- `GCP_PROJECT_ID` — GCP project ID
- `PULUMI_ACCESS_TOKEN` — Pulumi API token

## Database Migrations

Schema is managed via SQL migration files in `supabase/migrations/`:

| File                                      | Description                                |
|-------------------------------------------|--------------------------------------------|
| `001_create_schema.sql`                   | profiles, interviews, shadowing_requests   |
| `002_add_reason_to_shadowing_requests.sql`| Adds `reason` column for rejections        |
| `003_add_google_oauth_columns.sql`        | Adds Google OAuth token columns to profiles|

Hibernate runs in `validate` mode — it will **not** create or modify the schema. Apply migrations directly via Supabase.

## API Testing

A Postman collection is available in `postman/`:

1. Import `Interview_Hub.postman_collection.json` and `Interview_Hub.postman_environment.json`
2. Open `{{base_url}}/auth/google` in a browser and sign in with a `@gm2dev.com` account
3. Copy the JWT from the response and set it as the `jwt_token` environment variable
4. Use the collection endpoints to test interviews and shadowing requests

## License

TBD
