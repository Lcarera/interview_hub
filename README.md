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
  | Frontend           |          | API Gateway          |
  | Angular + nginx :80|          | Spring Cloud GW :8080|
  +--------------------+          +-----------+----------+
                                              |
                                  +-----------v----------+
                                  | GCP Cloud Run        |
                                  | Backend (Core)       |
                                  | Spring Boot :8082    |
                                  +-----------+----------+
                                              |
                         +--------------------+--------------------+
                         |                    |                    |
             +-----------v-----+  +-----------v-----+  +----------v--------+
             | Supabase        |  | Eureka Server   |  | Notification Svc  |
             | PostgreSQL      |  | Service Registry|  | RabbitMQ + Resend |
             +-----------+-----+  +-----------------+  +-------------------+
                         |
                    +----+----+
                    |         |
               Google    Google
               OAuth 2.0 Calendar
                          API v3
```

## Tech Stack

| Layer          | Technology                                  |
|----------------|---------------------------------------------|
| API Gateway    | Spring Cloud Gateway (WebFlux), Java 25     |
| Backend        | Spring Boot 4.0.2, Java 25, PostgreSQL      |
| Frontend       | Angular 21, Angular Material 21, TypeScript 5.9 |
| Service Discovery | Netflix Eureka                           |
| Messaging      | RabbitMQ (CloudAMQP in prod)                |
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
   - **API Gateway** on `http://localhost:8080` (public entry point for all API traffic)
   - **Backend (Core)** on internal port 8082 (not exposed externally — reached via Eureka)
   - **Eureka Server** on `http://localhost:8761` (service discovery dashboard)
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
├── services/
│   ├── core/                 # Spring Boot backend (Java 25, MVC)
│   ├── api-gateway/          # Spring Cloud Gateway (WebFlux, JWT validation)
│   ├── eureka-server/        # Netflix Eureka service registry
│   ├── notification-service/ # Email processing via RabbitMQ + Resend
│   └── shared/               # Shared DTOs between services
├── frontend/                 # Angular 21 SPA
├── infra/                    # Pulumi IaC (GCP Cloud Run, Secret Manager, etc.)
├── supabase/migrations/      # PostgreSQL schema migrations
├── postman/                  # Postman collection for API testing
├── .github/workflows/        # CI/CD pipeline (GitHub Actions)
├── compose.yaml              # Local Docker Compose (all services)
├── build.gradle              # Root Gradle config (multi-module monorepo)
└── CLAUDE.md                 # AI assistant instructions
```

See per-module documentation:
- [Frontend](frontend/README.md)
- [Infrastructure](infra/README.md)

## CI/CD

The GitHub Actions workflow (`.github/workflows/deploy.yml`) triggers on every push to `prod`:

1. Detects which services changed (backend, frontend, eureka, notification, gateway, infra, migrations)
2. Builds only changed service images with `./gradlew :services:<module>:bootBuildImage`
3. Pushes images to GCP Artifact Registry (tagged with git SHA)
4. Runs Supabase migrations if new SQL files were added
5. Deploys to GCP Cloud Run via `pulumi up`

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
