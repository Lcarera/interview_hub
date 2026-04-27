# Local K8s Validation (minikube) — Design Spec

**Date:** 2026-04-27
**Status:** Approved
**Author:** Lucho + Claudio

---

## Problem

All open GKE migration tickets (#94–#99) mix Kubernetes concepts with GCP-specific infrastructure (Workload Identity, SecretProviderClass, Terraform, DNS). Working on both simultaneously makes it hard to learn Kubernetes properly — when something breaks it's unclear whether the problem is in the K8s manifests or the GCP layer. Cloud deployment is paused until local validation is complete.

## Goal

Get all 5 Interview Hub services running on minikube using plain Kubernetes primitives (no GCP dependencies), validate the full routing chain locally, and build a solid mental model of the architecture before introducing GCP-specific layers.

---

## New GitHub Issues

Four new issues under a parent epic:

| Issue | Title | Blocked by |
|-------|-------|-----------|
| Parent | `[CHORE] Local K8s validation (minikube)` | — |
| Sub 1 | `[CHORE] Verify all 5 services in docker-compose` | Parent |
| Sub 2 | `[CHORE] Write local K8s manifests (no GCP deps)` | Sub 1 |
| Sub 3 | `[CHORE] Deploy to minikube and validate end-to-end` | Sub 2 |

Existing tickets #94–#99 each get one line added to their body:

> **Blocked by:** Local K8s validation epic (see #[parent issue number])

No other changes to #94–#99. Their content, acceptance criteria, and scope remain as written.

---

## Sub 1 — docker-compose smoke test

**Scope:** Confirm all 5 services start and pass health checks via `docker-compose up`.

Services: `api-gateway`, `core`, `calendar-service`, `notification-service`, `frontend`.

**Acceptance criteria:**
- `docker-compose up` starts without errors
- `GET /actuator/health` returns `{"status":"UP"}` on api-gateway, core, calendar-service, notification-service
- `GET /` returns HTTP 200 on frontend
- No `EUREKA_URL` env vars remain in `compose.yaml`

**Why first:** Establishes a known-good baseline before adding Kubernetes complexity. If a service fails in docker-compose, the problem is in application code or config — not K8s. If it passes, failures later are K8s problems.

---

## Sub 2 — Local K8s manifests

**Scope:** Create `interview_hub/k8s/` with all manifest files using local-safe variants.

### Files and local vs. GCP differences

| File | Local variant | GCP delta (addressed in #95/#96) |
|------|--------------|----------------------------------|
| `namespace.yaml` | `interview-hub-ns1` | None — identical in production |
| `service-accounts.yaml` | 5 KSAs, no WI annotations | Add `iam.gke.io/gcp-service-account` annotation per SA |
| `secrets.yaml` | Plain `Secret` resources, values from local `.env` | **Delete entirely** — replaced by SecretProviderClass at pod startup |
| `secret-provider-classes.yaml` | **Not created** | Created in #96 once WI + CSI driver exist in GKE |
| `deployments.yaml` | `imagePullPolicy: Never`, local image names | Swap image refs to Artifact Registry paths; change pull policy |
| `services.yaml` | 4 ClusterIP services (no service for notification-service) | None — identical in production |
| `ingress.yaml` | `kubernetes.io/ingress.class: nginx` (minikube addon) | Swap to `gce` + `kubernetes.io/ingress.global-static-ip-name` annotation |
| `managed-cert.yaml` | **Not created** — nginx handles TLS locally | Created in #96 for GCP-managed certificates |
| `hpa.yaml` | CPU-based, min 1 / max 5 on `core`, target 70% | None — identical in production |

### Key principle: `secrets.yaml` is local-only

In production, `SecretProviderClass` mounts secrets from GCP Secret Manager at pod startup — there are no `Secret` resources. Locally, `secrets.yaml` is the only secret mechanism. Understanding this distinction (and why it's replaced, not extended) is a core K8s vs. GCP learning outcome of this ticket.

### Ingress routing (same logic, different controller)

Local and production ingress rules are identical — only the annotation and controller change:

```
interview-hub.local  →  ingress  →  frontend:80
i-hub-be.local       →  ingress  →  api-gateway:8080
```

Use `.local` hostnames in the local `ingress.yaml`. Add them to `/etc/hosts` pointing at `minikube ip`.

**Acceptance criteria:**
- `interview_hub/k8s/` contains all files listed above (excluding GCP-only ones)
- `kubectl apply -f k8s/ --dry-run=client` passes with no errors
- No references to GCP project IDs, Artifact Registry paths, or SecretProviderClass in the local manifests

---

## Sub 3 — Minikube deploy and validate

**Scope:** Start minikube, apply manifests, and verify the full routing chain.

### Minikube setup

```bash
minikube start --addons=ingress,metrics-server
minikube image load <service>:<tag>   # for each of the 5 services
```

### Apply and verify

```bash
kubectl apply -f k8s/
kubectl get all -n interview-hub-ns1
kubectl rollout status deployment/core -n interview-hub-ns1
```

Add to `/etc/hosts` (replace `<minikube-ip>` with output of `minikube ip`):
```
<minikube-ip>  interview-hub.local  i-hub-be.local
```

### Routing chain to validate

```
browser → ingress → frontend (Angular SPA loads)
browser → i-hub-be.local/actuator/health → api-gateway → core → {"status":"UP"}
core    → calendar-service:8082 (FeignClient call succeeds)
core    → notification.emails (RabbitMQ message delivered to notification-service)
```

**Acceptance criteria:**
- `kubectl get all -n interview-hub-ns1` shows 5 running Deployments, 4 Services, 1 Ingress
- `https://interview-hub.local` loads the Angular SPA
- `https://i-hub-be.local/actuator/health` returns `{"status":"UP"}`
- An interview can be created end-to-end (triggers calendar + notification)
- HPA is visible: `kubectl get hpa -n interview-hub-ns1`
- `kubectl top pods -n interview-hub-ns1` shows metrics (metrics-server working)

---

## Impact on Existing GCP Tickets

No ticket content changes except adding one blocked-by line to each. After the local epic closes:

- **#96** body should be updated to reflect that `k8s/` already exists and the ticket scope narrows to "swap local variants for GCP variants" — not "create manifests from scratch." This update happens at the start of #96, not now.

### Execution order after local epic

```
Local epic closes
      ↓
#94 — Terraform: static IP + Kubernetes provider (no blockers)
      ↓
#95 — Terraform: IAM + Workload Identity (blocked by #94)
      ↓
#96 — Swap local manifests for GCP variants (blocked by #95)
      ↓
#97 — CI/CD pipeline rewrite (blocked by #96)
      ↓
#98 — DNS cutover (blocked by #97)
      ↓
#99 — Documentation update (blocked by #98)
```

---

## Out of Scope

- Feature development (no new application features in this spec)
- Helm charts (plain YAML only — add abstraction when you need it)
- Multi-namespace or multi-cluster setup
- Monitoring/alerting beyond what `kubectl top` and `metrics-server` provide locally
