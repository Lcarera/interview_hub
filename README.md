# Interview Hub

A fullstack application for managing technical interviews and shadowing requests. Built with Spring Boot 4.0.2 (Java 25) and Angular 21, deployed on GCP GKE with Cloudflare DNS.

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
  | GKE Pod            |          | GKE Pod              |
  | Frontend           |          | API Gateway          |
  | Angular + nginx :80|          | Spring Cloud GW :8080|
  +--------------------+          +-----------+----------+
                                              |
                                  +-----------v----------+
                                  | GKE Pod              |
                                  | Backend (Core)       |
                                  | Spring Boot :8082    |
                                  +-----------+----------+
                                              |
                       +----------------------+--------------------+
                       |                      |                    |
           +-----------v-----+  +-------------v-----+  +----------v--------+
           | Supabase        |  | Calendar Service  |  | Notification Svc  |
           | PostgreSQL      |  | GKE Pod :8082     |  | RabbitMQ + Resend |
           +-----------+-----+  +-------------------+  +-------------------+
                       |
                  +----+----+
                  |         |
             Google    Google
             OAuth 2.0 Calendar
                        API v3
```

Services communicate via Kubernetes DNS names (or env-var URLs for local dev).

## Tech Stack

| Layer          | Technology                                       |
|----------------|--------------------------------------------------|
| API Gateway    | Spring Cloud Gateway (WebFlux), Java 25          |
| Backend        | Spring Boot 4.0.2, Java 25, PostgreSQL           |
| Frontend       | Angular 21, Angular Material 21, TypeScript 5.9  |
| Messaging      | RabbitMQ (in-cluster, `rabbitmq:4-management`)   |
| Infrastructure | Terraform (GCP GKE), kubectl manifests           |
| DNS/CDN        | Cloudflare (DNS proxy)                           |
| Auth           | Google OAuth 2.0 (@gm2dev.com), HMAC-SHA256 JWT  |
| CI/CD          | GitHub Actions (app), Cloud Build (infra)        |
| Database       | Supabase (PostgreSQL)                            |
| Package Mgr    | Gradle (backend), Bun 1.2 (frontend)             |

## Prerequisites

- Java 25 (Eclipse Temurin)
- Bun 1.2+
- Docker & Docker Compose
- Cloudflare account (for DNS management)

## Quick Start

1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd interview_hub
   ```

2. **Create a `.env` file** in the project root with required environment variables (see [Environment Variables](#environment-variables)).

3. **Build the backend Docker images:**
   ```bash
   ./gradlew bootBuildImage
   ```

4. **Start all services:**
   ```bash
   docker compose up
   ```

   This starts:
   - **API Gateway** on `http://localhost:8080` (public entry point for all API traffic)
   - **Backend (Core)** on internal port 8082
   - **Calendar Service** on internal port 8082
   - **Notification Service** (email via RabbitMQ)
   - **Frontend** on `http://localhost` (port 80)

5. **For frontend-only development** (assumes backend is running on port 8080):
   ```bash
   cd frontend
   bun install
   bun run start   # http://localhost:4200
   ```

## Environment Variables

| Variable                      | Description                                    | Default                  |
|-------------------------------|------------------------------------------------|--------------------------|
| `DB_URL`                      | PostgreSQL JDBC URL                            | -                        |
| `DB_USERNAME`                 | Database username                              | -                        |
| `DB_PASSWORD`                 | Database password                              | -                        |
| `GOOGLE_CLIENT_ID`            | Google OAuth 2.0 client ID                     | -                        |
| `GOOGLE_CLIENT_SECRET`        | Google OAuth 2.0 client secret                 | -                        |
| `JWT_SIGNING_SECRET`          | HMAC-SHA256 key for JWT signing (min 32 bytes) | -                        |
| `APP_BASE_URL`                | Backend base URL for OAuth callbacks           | `http://localhost:8080`  |
| `FRONTEND_URL`                | Frontend URL for post-auth redirects           | `http://localhost`       |
| `CORE_URL`                    | URL for api-gateway → core routing             | `http://localhost:8082`  |
| `CALENDAR_SERVICE_URL`        | URL for core → calendar-service calls          | `http://localhost:8082`  |
| `GOOGLE_CALENDAR_REFRESH_TOKEN` | Calendar OAuth refresh token                 | -                        |
| `RESEND_API_KEY`              | Resend API key for sending emails              | -                        |

## Project Structure

```
interview_hub/
├── services/
│   ├── core/                 # Spring Boot backend (Java 25, MVC)
│   ├── api-gateway/          # Spring Cloud Gateway (WebFlux, JWT validation)
│   ├── notification-service/ # Email processing via RabbitMQ + Resend
│   ├── calendar-service/     # Google Calendar API microservice
│   └── shared/               # Shared DTOs between services
├── frontend/                 # Angular 21 SPA
├── k8s/                      # Kubernetes manifests (minikube local + GKE deployment)
├── supabase/migrations/      # PostgreSQL schema migrations
├── postman/                  # Postman collection for API testing
├── compose.yaml              # Local Docker Compose (all services)
├── build.gradle              # Root Gradle config (multi-module monorepo)
└── CLAUDE.md                 # AI assistant instructions
```

See per-module documentation:
- [Backend (core)](services/core/src/README.md)
- [Frontend](frontend/README.md)

## CI/CD

CI/CD pipeline is being rebuilt as part of the GKE migration (#97). The new model uses two repos:
- **App repo** (this repo) — GitHub Actions builds and pushes Docker images on push to `prod`
- **Infra repo** (`tf-infra-cio-interview-hub`) — Cloud Build deploys via Terraform on git tag

## Database Migrations

Schema is managed via SQL migration files in `supabase/migrations/`:

| File                                        | Description                                              |
|---------------------------------------------|----------------------------------------------------------|
| `001_create_schema.sql`                     | profiles, interviews, shadowing_requests tables          |
| `002_add_reason_to_shadowing_requests.sql`  | Adds `reason` column for rejections                      |
| `003_add_google_oauth_columns.sql`          | Adds Google OAuth token columns to profiles              |
| `004_create_candidates_table.sql`           | Creates candidates table, adds candidate_id FK           |
| `005_add_email_password_auth.sql`           | Adds password_hash, email_verified, verification_tokens  |
| `006_seed_admin_user.sql`                   | Promotes luciano.carera@gm2dev.com to admin role         |
| `007_drop_calendar_and_token_columns.sql`   | Drops legacy Google token columns from profiles          |

Hibernate runs in `validate` mode — it will **not** create or modify the schema. Apply migrations directly via Supabase.

## API Testing

A Postman collection is available in `postman/`:

1. Import `Interview_Hub.postman_collection.json` and `Interview_Hub.postman_environment.json`
2. Open `{{base_url}}/auth/google` in a browser and sign in with a `@gm2dev.com` account
3. Copy the JWT from the response and set it as the `jwt_token` environment variable
4. Use the collection endpoints to test interviews and shadowing requests

## License

TBD
