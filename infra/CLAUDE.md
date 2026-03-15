# Infrastructure (Pulumi + GCP)

Pulumi Python project managing GCP infrastructure for Interview Hub. Stack: `prod`.

## Commands

```bash
cd infra
source venv/bin/activate   # Python virtualenv
pulumi up                  # preview + deploy
pulumi preview             # dry-run only
pulumi stack output        # show exported values (registry_url, backend_url, frontend_url)
```

## Module Structure

- `__main__.py` — Entrypoint. Imports all modules for side effects and exports stack outputs.
- `registry.py` — Artifact Registry repo (`interview-hub`) for Docker images.
- `iam.py` — Cloud Run service account (`interview-hub-cloudrun`) with Secret Manager access.
- `secrets.py` — GCP Secret Manager secrets (8 secrets: DB creds, Google OAuth, JWT, mail password, service account key). Secret values are set manually via `gcloud`, not in code. **Note:** `MAIL_PASSWORD` and SMTP env vars in `cloudrun.py` are stale — backend now uses Resend SDK with `RESEND_API_KEY`. Infra needs updating to replace SMTP config with `RESEND_API_KEY` secret.
- `cloudrun.py` — Two Cloud Run v2 services (`backend`, `frontend`) with env vars, health probes, and public invoker bindings.

## Config Values (`Pulumi.prod.yaml`)

- `gcp:project` / `gcp:region` — GCP project and region
- `interview-hub-infra:domain` — Frontend custom domain
- `interview-hub-infra:backend_domain` — Backend custom domain
- `interview-hub-infra:backend_image` / `frontend_image` — Set by CI at deploy time; fallback to `"placeholder"` for secrets-only `pulumi up`

## Key Design Decisions

- **Secrets vs plain env vars:** Sensitive values (DB creds, API keys, passwords) go through Secret Manager (`secrets.py` → `_secret_envs` in `cloudrun.py`). Non-sensitive config (app URLs, calendar ID) are plain env vars.
- **TODO:** SMTP env vars (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`) and `MAIL_PASSWORD` secret are stale — replace with `RESEND_API_KEY` secret to match backend's Resend SDK migration.
- **Image config is optional:** `config.get()` with `"placeholder"` fallback allows running `pulumi up` to update secrets/env vars without a full CI image build. CI always sets the real image URI.
- **Public ingress:** Both services use `INGRESS_TRAFFIC_ALL` + `allUsers` invoker, accessed through Cloudflare DNS proxy.
- **No local state:** Pulumi state is managed remotely (Pulumi Cloud). No state files in the repo.
