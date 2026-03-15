# Interview Hub — Infrastructure

Pulumi (Python) Infrastructure as Code for deploying Interview Hub to GCP.

## Prerequisites

- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- Python 3.12+
- GCP project with billing enabled
- `gcloud` CLI authenticated

## Architecture

Pulumi provisions the following GCP resources:

```
┌─────────────────────────────────────────────────────────────┐
│ GCP Project: interview-hub-prod                             │
│ Region: southamerica-east1                                  │
│                                                             │
│  ┌──────────────────┐                                       │
│  │ Artifact Registry │ ◄── Docker images (backend/frontend) │
│  │ interview-hub     │                                       │
│  └──────────────────┘                                       │
│                                                             │
│  ┌──────────────────┐    ┌──────────────────┐               │
│  │ Cloud Run        │    │ Cloud Run        │               │
│  │ backend (:8080)  │    │ frontend (:80)   │               │
│  │ (Spring Boot)    │    │ (nginx + Angular)│               │
│  └────────┬─────────┘    └──────────────────┘               │
│           │                                                 │
│  ┌────────▼─────────┐                                       │
│  │ Secret Manager   │                                       │
│  │ (8 secrets)      │                                       │
│  └──────────────────┘                                       │
│                                                             │
│  ┌──────────────────┐                                       │
│  │ Service Account  │ roles/secretmanager.secretAccessor    │
│  │ cloudrun-sa      │                                       │
│  └──────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

## Module Descriptions

| File             | Purpose                                                      |
|------------------|--------------------------------------------------------------|
| `__main__.py`    | Pulumi entry point — imports all modules, exports stack outputs |
| `iam.py`         | Creates `interview-hub-cloudrun` service account + secret accessor IAM binding |
| `secrets.py`     | Creates 8 GCP Secret Manager secrets (DB, OAuth, JWT, Resend API key, service account key) |
| `registry.py`    | Creates Artifact Registry Docker repository                  |
| `cloudrun.py`    | Deploys backend and frontend Cloud Run services              |

## GCP Resources

### Service Account (`iam.py`)

- **ID:** `interview-hub-cloudrun`
- **Role:** `roles/secretmanager.secretAccessor` (reads secrets at runtime)

### Secret Manager (`secrets.py`)

| Secret Name                                | Maps To                       |
|--------------------------------------------|-------------------------------|
| `interview-hub-db-url`                     | `DB_URL`                      |
| `interview-hub-db-username`                | `DB_USERNAME`                 |
| `interview-hub-db-password`                | `DB_PASSWORD`                 |
| `interview-hub-google-client-id`           | `GOOGLE_CLIENT_ID`            |
| `interview-hub-google-client-secret`       | `GOOGLE_CLIENT_SECRET`        |
| `interview-hub-jwt-signing-secret`         | `JWT_SIGNING_SECRET`          |
| `interview-hub-resend-api-key`             | `RESEND_API_KEY`              |
| `interview-hub-google-service-account-key` | `GOOGLE_SERVICE_ACCOUNT_KEY`  |

All secrets use auto-replication. Pulumi creates the secret containers — values must be set manually:

```bash
echo -n "your-secret-value" | gcloud secrets versions add interview-hub-db-url --data-file=-
```

### Artifact Registry (`registry.py`)

- **Repository:** `interview-hub` (Docker format)
- **Location:** `southamerica-east1`
- **URL:** `southamerica-east1-docker.pkg.dev/interview-hub-prod/interview-hub`

### Cloud Run Services (`cloudrun.py`)

**Backend (`interview-hub-backend`):**
- Image: set via CI/CD (required — no fallback default)
- Port: 8080
- Resources: 1 GiB memory, 1000m CPU
- Min instances: 0 (scales to zero)
- Health check: startup probe to `/actuator/health`
- Env vars: 8 secrets from Secret Manager + `APP_BASE_URL`, `FRONTEND_URL`, `MAIL_FROM`, `GOOGLE_CALENDAR_ID`
- IAM: `allUsers` invoker (publicly accessible)

**Frontend (`interview-hub-frontend`):**
- Image: set via CI/CD
- Port: 80
- Min instances: 0
- IAM: `allUsers` invoker (publicly accessible)

## Stack Configuration

### `Pulumi.yaml`

```yaml
name: interview-hub-infra
runtime:
  name: python
  options:
    virtualenv: venv
```

### `Pulumi.prod.yaml`

| Config Key                             | Value                        |
|----------------------------------------|------------------------------|
| `gcp:project`                          | `interview-hub-prod`         |
| `gcp:region`                           | `southamerica-east1`         |
| `interview-hub-infra:domain`           | `interview-hub.lcarera.dev`  |
| `interview-hub-infra:backend_domain`   | `i-hub-be.lcarera.dev`       |

The `domain` config sets `FRONTEND_URL` on the backend. The `backend_domain` config sets `APP_BASE_URL`.

## Stack Outputs

| Output          | Description                                              |
|-----------------|----------------------------------------------------------|
| `registry_url`  | Artifact Registry URL for pushing Docker images          |
| `backend_url`   | Cloud Run URL for the backend service                    |
| `frontend_url`  | Cloud Run URL for the frontend service                   |

## Deployment

### Manual deployment

```bash
cd infra
python -m venv venv
source venv/bin/activate    # Linux/macOS
pip install -r requirements.txt

pulumi stack select prod
pulumi up
```

### CI/CD (GitHub Actions)

The `deploy.yml` workflow handles deployment automatically on push to `main`:

1. Builds and pushes Docker images to Artifact Registry (tagged with git SHA)
2. Sets `backend_image` and `frontend_image` Pulumi config values
3. Runs `pulumi up --yes` to deploy updated images to Cloud Run

Required GitHub secrets: `GCP_SA_KEY`, `GCP_PROJECT_ID`, `PULUMI_ACCESS_TOKEN`

## Dependencies

```
pulumi>=3.0.0,<4.0.0
pulumi-gcp>=7.0.0,<8.0.0
```
