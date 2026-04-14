# Infrastructure (Pulumi + GCP)

Pulumi Python project managing GCP infrastructure for Interview Hub. Stack: `prod`.

## Commands

```bash
cd infra
source venv/bin/activate   # Python virtualenv
pulumi up                  # preview + deploy
pulumi preview             # dry-run only
pulumi stack output        # show exported values (registry_url, backend_url, frontend_url, eureka_url, notification_url, gateway_url)
```

## Module Structure

- `__main__.py` — Entrypoint. Imports all modules for side effects and exports stack outputs.
- `registry.py` — Artifact Registry repo (`interview-hub`) for Docker images.
- `iam.py` — Cloud Run service account (`interview-hub-cloudrun`) with Secret Manager access.
- `secrets.py` — GCP Secret Manager secrets (8 secrets: DB creds, Google OAuth, JWT, Resend API key, Google Calendar refresh token). Secret values are set manually via `gcloud`, not in code.
- `cloudtasks.py` — Cloud Tasks queue (`email-queue`) for async email sending with 2 req/s rate limiting. Grants enqueuer role to Cloud Run SA.
- `cloudrun.py` — Six Cloud Run v2 services (`backend`, `frontend`, `eureka-server`, `notification-service`, `api-gateway`, `calendar-service`) with env vars, health probes, and invoker bindings.

## Config Values (`Pulumi.prod.yaml`)

- `gcp:project` / `gcp:region` — GCP project and region
- `interview-hub-infra:domain` — Frontend custom domain
- `interview-hub-infra:backend_domain` — Backend custom domain
- `interview-hub-infra:cloudtasks_worker_url` — Cloud Run service URL for Cloud Tasks HTTP targets (bypasses Cloudflare, ensures OIDC audience matches)
- `interview-hub-infra:backend_image` / `frontend_image` / `eureka_image` / `notification_image` / `gateway_image` / `calendar_image` — Set by CI at deploy time; fallback to `"placeholder"` for secrets-only `pulumi up`

## Cloud Run Scaling Notes

- **Eureka server:** `min_instance_count=1` required — heartbeat model breaks on scale-to-zero; all registrations are lost
- **API Gateway:** `min_instance_count=1` required — public entry point; scale-to-zero adds cold start latency on every idle request
- **RabbitMQ consumers:** `min_instance_count=1` required — persistent AMQP TCP connection; scale-to-zero = silent queue backlog
- **RabbitMQ on GCP:** no managed offering; use CloudAMQP free tier (1M msg/month). Store AMQP URL as a Secret Manager secret (`RABBITMQ_URL`).
- **Eureka URL env var:** must include `/eureka/` suffix — `eureka_service.uri.apply(lambda u: u + "/eureka/")`
- **New services follow the pattern:** add `foo_image = config.get("foo_image") or "placeholder"`, define the Cloud Run service, add IAM invoker binding, export the URI in `__main__.py`, add change filter + build step + Pulumi config block in `deploy.yml`

## Key Design Decisions

- **Secrets vs plain env vars:** Sensitive values (DB creds, API keys, Resend key) go through Secret Manager (`secrets.py` → `_secret_envs` in `cloudrun.py`). Non-sensitive config (app URLs, calendar ID, mail from address) are plain env vars.
- **Image config is optional:** `config.get()` with `"placeholder"` fallback allows running `pulumi up` to update secrets/env vars without a full CI image build. CI always sets the real image URI.
- **Public ingress:** All services use `INGRESS_TRAFFIC_ALL` + `allUsers` invoker, accessed through Cloudflare DNS proxy.
- **API Gateway:** `api-gateway` is the public entry point; `backend_domain` DNS points to gateway. Core's `APP_BASE_URL` uses `backend_domain` so OAuth callbacks route through the gateway.
- **No local state:** Pulumi state is managed remotely (Pulumi Cloud). No state files in the repo.
