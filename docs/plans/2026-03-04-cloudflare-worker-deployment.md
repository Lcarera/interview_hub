# Cloudflare Worker Deployment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the GCP Global HTTPS Load Balancer (~$18/month) with a free Cloudflare Worker that proxies `interview-hub.lcarera.dev` to the two Cloud Run services.

**Architecture:** Two public Cloud Run services (backend + frontend) sit directly on the internet. A Cloudflare Worker bound to `interview-hub.lcarera.dev/*` inspects the request path and forwards it to the correct service. Cloudflare handles TLS for the custom domain. No GCP load balancer resources exist after this change.

**Tech Stack:** Pulumi Python (infra changes), Cloudflare Workers (Wrangler CLI), GCP Cloud Run v2.

---

## Prerequisites

- Wrangler CLI installed (`npm install -g wrangler` or `bun add -g wrangler`)
- Cloudflare account with `lcarera.dev` zone already managed there
- Pulumi CLI authenticated, `prod` stack selected (`pulumi stack select prod` from `infra/`)

---

## Task 1: Remove the Load Balancer from Pulumi

**Files:**
- Delete: `infra/loadbalancer.py`
- Modify: `infra/__main__.py`

**Step 1: Delete loadbalancer.py**

Delete `infra/loadbalancer.py` entirely.

**Step 2: Update `infra/__main__.py`**

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects
from secrets import secrets  # noqa: F401 — imported for side effects
from cloudrun import backend_service, frontend_service

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
```

**Step 3: Commit**

```bash
git add infra/__main__.py
git rm infra/loadbalancer.py
git commit -m "feat(infra): remove GCP global HTTPS load balancer"
```

---

## Task 2: Open Cloud Run Ingress to Public Traffic

**Files:**
- Modify: `infra/cloudrun.py`

**Step 1: Change ingress on both Cloud Run services**

In `infra/cloudrun.py`, change `ingress` on both `gcp.cloudrunv2.Service` resources:

```python
# backend_service and frontend_service — both:
ingress="INGRESS_TRAFFIC_ALL",
```

Also update the comment above the backend IAM binding:

```python
# Grant allUsers invoker so Cloudflare Worker can call the services without
# identity tokens. INGRESS_TRAFFIC_ALL allows direct internet access — the
# Worker is the only intended entry point, but the .run.app URLs are public.
```

**Step 2: Run pulumi up (applies both Task 1 and Task 2)**

```bash
cd infra
pulumi up --yes
```

Expected: 9+ resources destroyed (LB stack), 2 Cloud Run services updated (ingress change). Stack completes without errors.

**Step 3: Verify Cloud Run services are publicly reachable**

```bash
curl https://interview-hub-backend-qeczodae3q-rj.a.run.app/actuator/health
curl https://interview-hub-frontend-qeczodae3q-rj.a.run.app/
```

Both should return HTTP 200.

**Step 4: Commit**

```bash
git add infra/cloudrun.py
git commit -m "feat(infra): set Cloud Run ingress to INGRESS_TRAFFIC_ALL for Cloudflare Worker"
```

---

## Task 3: Create the Cloudflare Worker

**Files:**
- Create: `cloudflare/worker.js`
- Create: `cloudflare/wrangler.toml`

**Step 1: Create `cloudflare/worker.js`**

```javascript
const BACKEND_URL = 'https://interview-hub-backend-qeczodae3q-rj.a.run.app';
const FRONTEND_URL = 'https://interview-hub-frontend-qeczodae3q-rj.a.run.app';

const BACKEND_PREFIXES = [
  '/auth/google',
  '/auth/token',
  '/interviews',
  '/shadowing-requests',
  '/actuator',
];

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const isBackend = BACKEND_PREFIXES.some(
      (p) => url.pathname === p || url.pathname.startsWith(p + '/')
    );
    const target = (isBackend ? BACKEND_URL : FRONTEND_URL) + url.pathname + url.search;
    return fetch(target, {
      method: request.method,
      headers: request.headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
    });
  },
};
```

Routing mirrors the former URL map exactly:
- `/auth/google`, `/auth/google/*` → backend (OAuth initiation + Google callback)
- `/auth/token` → backend (Postman token endpoint)
- `/interviews`, `/interviews/*` → backend
- `/shadowing-requests`, `/shadowing-requests/*` → backend
- `/actuator`, `/actuator/*` → backend (health check)
- `/auth/callback` and everything else → frontend (Angular SPA)

**Step 2: Create `cloudflare/wrangler.toml`**

```toml
name = "interview-hub"
main = "worker.js"
compatibility_date = "2024-01-01"

routes = [
  { pattern = "interview-hub.lcarera.dev/*", zone_name = "lcarera.dev" }
]
```

**Step 3: Commit**

```bash
git add cloudflare/
git commit -m "feat(cloudflare): add Worker reverse proxy and Wrangler config"
```

---

## Task 4: Deploy the Worker

**Step 1: Authenticate with Cloudflare**

```bash
wrangler login
```

Opens a browser — authorize with your Cloudflare account.

**Step 2: Deploy**

```bash
cd cloudflare
wrangler deploy
```

Expected output:
```
Total Upload: ~1 KiB / gzip: ~0.5 KiB
Uploaded interview-hub (Xs)
Published interview-hub (Xs)
  interview-hub.lcarera.dev/*
```

**Step 3: Delete the old DNS A record**

In the Cloudflare dashboard for `lcarera.dev` → DNS, delete the A record:
```
interview-hub  A  34.54.221.170
```
This was the old LB static IP. With a Worker route active, it is no longer needed.

---

## Task 5: Smoke Test

**Step 1: Backend health (Worker routes to backend)**

```bash
curl -i https://interview-hub.lcarera.dev/actuator/health
```
Expected: `200 {"status":"UP"}`

**Step 2: Frontend (Worker routes to frontend)**

```bash
curl -i https://interview-hub.lcarera.dev/
```
Expected: `200` with Angular HTML shell.

**Step 3: OAuth initiation (confirms backend routing for `/auth/google`)**

```bash
curl -i https://interview-hub.lcarera.dev/auth/google
```
Expected: `302` redirect to `accounts.google.com`.

**Step 4: Angular client-side route (must NOT go to backend)**

```bash
curl -i https://interview-hub.lcarera.dev/auth/callback
```
Expected: `200` with Angular HTML shell (nginx `try_files` fallback).

---

## What Does Not Change

- `infra/registry.py`, `infra/iam.py`, `infra/secrets.py` — untouched
- `infra/Pulumi.prod.yaml` — untouched
- Backend Secret Manager values (`APP_BASE_URL`, `FRONTEND_URL` stay as `https://interview-hub.lcarera.dev`)
- `frontend/nginx.prod.conf` — already serves static files only
- GitHub Actions workflow — untouched (Worker deploy can be added to CI later)
- Google OAuth redirect URI — stays as `https://interview-hub.lcarera.dev/auth/google/callback`
