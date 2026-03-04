# Pulumi GCP Deployment Design

**Date:** 2026-03-03
**Status:** Approved

## Overview

Deploy Interview Hub to Google Cloud Platform using Pulumi (Python) for infrastructure-as-code. The stack targets a single production environment with two Cloud Run services (backend + frontend) behind a Cloud Load Balancer, with Artifact Registry for images and Secret Manager for secrets. CI/CD is handled by GitHub Actions.

## Architecture

### Compute

- **Backend:** Cloud Run service (`interview-hub-backend`) running the Spring Boot container on port 8080.
- **Frontend:** Cloud Run service (`interview-hub-frontend`) running the nginx/Angular container on port 80.
- Both services are **private** (no unauthenticated access). The Load Balancer reaches them via serverless NEGs.

### Traffic Flow

```
User browser
    │
    ▼
Global HTTPS Load Balancer (static IP + managed SSL cert)
    │
    ├── /auth/*
    ├── /interviews
    ├── /shadowing-requests      ──► interview-hub-backend (Cloud Run)
    └── /actuator
    │
    └── /*                       ──► interview-hub-frontend (Cloud Run)
```

The URL map at the Load Balancer level replaces the nginx `proxy_pass` blocks. The frontend nginx config is simplified to serve only static Angular assets.

### Image Registry

GCP Artifact Registry hosts Docker images for both services. Tagged by Git SHA on each CI run.

### Secrets

All runtime secrets are stored in GCP Secret Manager and injected into the backend Cloud Run service as environment variables via Cloud Run's native Secret Manager integration. No SDK calls needed in application code.

Secrets managed:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `JWT_SIGNING_SECRET`
- `TOKEN_ENCRYPTION_KEY`

Secret values are set manually once (or via `gcloud` in CI); Pulumi manages the secret metadata only.

### IAM

A dedicated GCP Service Account is created for Cloud Run with:
- `roles/secretmanager.secretAccessor` — read secrets at runtime
- `roles/run.invoker` — allow LB to invoke Cloud Run services

## GCP Resources

| Resource | Description |
|---|---|
| `google.artifactregistry.Repository` | Docker image registry |
| `google.cloudrun.v2.Service` (×2) | Backend and frontend services |
| `google.compute.GlobalAddress` | Static IP for the Load Balancer |
| `google.compute.ManagedSslCertificate` | Auto-provisioned HTTPS cert |
| `google.compute.BackendService` (×2) | LB backends with serverless NEGs |
| `google.compute.UrlMap` | Routes API paths to backend, `/*` to frontend |
| `google.compute.TargetHttpsProxy` | HTTPS termination |
| `google.compute.GlobalForwardingRule` | Binds static IP to HTTPS proxy |
| `google.secretmanager.Secret` (×7) | One per app secret |
| `google.serviceaccount.Account` | Cloud Run runtime service account |

## Pulumi Project Structure

```
infra/
├── __main__.py         # Entry point — wires all components
├── registry.py         # Artifact Registry repository
├── secrets.py          # Secret Manager secrets
├── cloudrun.py         # Backend + frontend Cloud Run services
├── loadbalancer.py     # LB, URL map, SSL cert, forwarding rule
├── iam.py              # Service accounts + role bindings
├── Pulumi.yaml         # Project config (name, runtime: python)
└── Pulumi.prod.yaml    # Stack config (GCP project, region, domain)
```

## CI/CD Pipeline

**Trigger:** Push to `main` branch.

**Steps:**
1. Authenticate to GCP using a CI service account key (`GCP_SA_KEY` GitHub secret).
2. Build and push backend image (`./gradlew bootBuildImage`) to Artifact Registry.
3. Build and push frontend image (`docker build frontend/`) to Artifact Registry. Images tagged with Git SHA.
4. Run `pulumi up --yes` from `infra/` — updates Cloud Run services to new image digests.

**GitHub Secrets required:**
| Secret | Purpose |
|---|---|
| `GCP_SA_KEY` | CI service account JSON key |
| `PULUMI_ACCESS_TOKEN` | Pulumi Cloud state backend token |
| App secrets (`DB_URL`, etc.) | Seeded into GCP Secret Manager on first deploy |

**CI Service Account roles:**
- `roles/run.admin`
- `roles/artifactregistry.writer`
- `roles/secretmanager.admin`
- `roles/compute.loadBalancerAdmin`
- `roles/iam.serviceAccountUser`

**Pulumi state backend:** Pulumi Cloud (free tier).

## Health Checks

- **Backend:** `/actuator/health` (startup + liveness probe).
- **Frontend:** Default HTTP check on `/`.

## Rollback

- Cloud Run retains the last 3 revisions. Traffic only shifts to a new revision after health checks pass; failed deployments leave the previous revision serving.
- Pulumi stack history tracks all updates. Rolling back means re-running `pulumi up` with the previous image digest.
- All image tags are retained in Artifact Registry.

## What Changes in the Existing App

| File | Change |
|---|---|
| `frontend/nginx.conf` | Remove `proxy_pass` blocks (routing moves to LB URL map) |
| Backend code | No change |
| Frontend code | No change |

## What Pulumi Does NOT Manage

- Supabase DB schema (managed via `supabase/migrations/`)
- Secret values (set manually or via `gcloud` once)
- Google OAuth app credentials (configured in Google Cloud Console)
