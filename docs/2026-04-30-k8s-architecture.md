# Kubernetes Architecture — Interview Hub

**Date:** 2026-04-30
**Status:** Current (minikube local)
**Scope:** How the k8s manifests work, what each object does, and how a request travels through the cluster.

---

## 1. Bird's-Eye View

```
OUTSIDE
  │
  ▼
Ingress (nginx / host-based routing)
  ├── interview-hub.local  ──►  frontend Service :80  ──►  frontend Pod
  └── i-hub-be.local       ──►  api-gateway Service :8080  ──►  api-gateway Pod
                                                                      │
                                                                      │ HTTP (K8s DNS)
                                                                      ▼
                                                             core Service :8080  ──►  core Pod
                                                               │           │
                                                    FeignClient│           │AMQP
                                                               ▼           ▼
                                              calendar-service  rabbitmq Service  ──►  rabbitmq Pod
                                              Service :8082           │
                                                │                     │ AMQP
                                                ▼                     ▼
                                           calendar-service Pod  notification-service Pod
```

- Everything lives in the `interview-hub-ns1` namespace.
- Pods talk to each other using **Kubernetes DNS**: a Service named `core` in namespace `interview-hub-ns1` is reachable at `http://core:8080` from any other pod in the same namespace.
- `notification-service` has **no ClusterIP Service** — it receives work via AMQP, not HTTP calls.

---

## 2. The Kubernetes Objects (what each file does)

### `namespace.yaml` — Isolation boundary

```yaml
kind: Namespace
metadata:
  name: interview-hub-ns1
```

A namespace is a virtual cluster within the real cluster. All Interview Hub objects live inside `interview-hub-ns1`. This means:
- `kubectl get pods` won't show them; `kubectl get pods -n interview-hub-ns1` will.
- DNS names within the namespace are short (`http://core:8080`); from outside they'd be `http://core.interview-hub-ns1.svc.cluster.local:8080`.
- Resource quotas and RBAC policies can be applied per namespace.

---

### `service-accounts.yaml` — Kubernetes identities

```yaml
kind: ServiceAccount
metadata:
  name: backend-sa
  namespace: interview-hub-ns1
```

Every pod runs as a Kubernetes Service Account (KSA). We create one per workload:

| KSA | Used by |
|-----|---------|
| `gateway-sa` | api-gateway |
| `backend-sa` | core |
| `calendar-sa` | calendar-service |
| `notification-sa` | notification-service |
| `frontend-sa` | frontend |

**Why separate SAs?** In production (GKE), each KSA will be bound to a distinct GCP Service Account via Workload Identity. That binding controls which GCP Secret Manager secrets the pod can read. Using one SA per workload means the calendar-service can't accidentally read the database credentials — they're on different SAs with different IAM bindings.

Locally (minikube), the SAs are inert — no GCP bindings exist. They're created now so the structure is identical to production.

---

### `secrets.yaml` / `secrets.example.yaml` — Sensitive configuration

```yaml
kind: Secret
metadata:
  name: core-secret
type: Opaque
stringData:
  DB_URL: "jdbc:postgresql://..."
  DB_PASSWORD: "..."
```

`stringData` lets you write plain text values; Kubernetes base64-encodes them at apply time.

Each workload has its own Secret containing only the credentials it needs:

| Secret | Consumed by | Contents |
|--------|-------------|----------|
| `gateway-secret` | api-gateway | `JWT_SIGNING_SECRET` |
| `core-secret` | core | DB creds, Google OAuth creds, JWT secret |
| `calendar-secret` | calendar-service | Google OAuth creds, Calendar ID, refresh token |
| `notification-secret` | notification-service | Resend API key, `MAIL_FROM`, `FRONTEND_URL` |
| `rabbitmq-secret` | rabbitmq, core, notification-service | RabbitMQ username + password |

Pods reference secrets in two ways (both used here):

**`envFrom.secretRef`** — injects every key in the secret as an env var:
```yaml
envFrom:
  - secretRef:
      name: core-secret
# Result: DB_URL, DB_PASSWORD, etc. all set as env vars in the container
```

**`env.valueFrom.secretKeyRef`** — injects a single key:
```yaml
env:
  - name: RABBITMQ_DEFAULT_USER
    valueFrom:
      secretKeyRef:
        name: rabbitmq-secret
        key: RABBITMQ_DEFAULT_USER
```

The `RABBITMQ_URL` for `core` and `notification-service` uses **Kubernetes variable interpolation** — it references other env vars in the same pod:
```yaml
- name: RABBITMQ_URL
  value: "amqp://$(RABBITMQ_USER):$(RABBITMQ_PASS)@rabbitmq:5672"
```
The `$(VAR)` syntax is resolved by kubelet before the container starts. This lets us build the URL from two secret values without duplicating the full URL in the secret.

**Local vs. GKE:** `secrets.yaml` is local-only (gitignored). In GKE it's replaced by `SecretProviderClass` resources that mount secrets from GCP Secret Manager at pod startup — there are no `Secret` objects in production.

---

### `deployments.yaml` — The workloads

A `Deployment` declares the desired state: "run N replicas of this container image with these env vars and probes." The Deployment controller continuously reconciles actual state to match.

```yaml
kind: Deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: core       # Deployment owns pods with this label
  template:
    metadata:
      labels:
        app: core     # Pod gets this label
    spec:
      serviceAccountName: backend-sa
      containers:
        - name: core
          image: interview-hub:0.0.1-SNAPSHOT
          imagePullPolicy: Never   # minikube-only: image is in local Docker, never pull
```

**`imagePullPolicy: Never`** tells Kubernetes not to contact a registry. This works because we build images directly into minikube's Docker daemon (`eval $(minikube docker-env)` redirects `docker build` to minikube). In GKE, this becomes `Always` or `IfNotPresent` with images from Artifact Registry.

#### Health probes — why three exist

Every Spring Boot service uses all three probes:

```yaml
startupProbe:        # Is the app done starting up?
  httpGet:
    path: /actuator/health
    port: 8080
  failureThreshold: 30  # 30 × 10s = 5 minutes before giving up
  periodSeconds: 10

readinessProbe:      # Is the app ready to receive traffic?
  httpGet:
    path: /actuator/health
    port: 8080
  periodSeconds: 10

livenessProbe:       # Is the app still alive? Kill if not.
  httpGet:
    path: /actuator/health
    port: 8080
  periodSeconds: 15
```

The three probes have distinct jobs:

| Probe | Kubernetes action on failure | When it runs |
|-------|------------------------------|--------------|
| `startupProbe` | Does nothing until it succeeds; **disables** liveness/readiness while running | During startup only |
| `readinessProbe` | Removes pod from Service endpoints (stops routing traffic to it) | After startup, continuously |
| `livenessProbe` | Kills and restarts the container | After startup, continuously |

**Why `startupProbe` instead of `initialDelaySeconds`:**  
JVM startup under resource pressure can take 60–90 seconds. `initialDelaySeconds: 60` would either kill fast-starting containers for being "slow" or race with slow ones. `startupProbe` with `failureThreshold: 30, periodSeconds: 10` gives exactly 5 minutes — and crucially, it disables liveness until the app is up, so a slow-starting JVM is never killed mid-startup.

**RabbitMQ uses `tcpSocket` not HTTP:**
```yaml
readinessProbe:
  tcpSocket:
    port: 5672   # Just checks the port is open
```
The exec probe (`rabbitmq-diagnostics ping`) has a 1-second default timeout. During RabbitMQ's feature-flag initialization, it doesn't respond in time, fails the probe, and gets killed — corrupting mnesia state on the next restart. `tcpSocket` just checks that the port accepts connections, which works reliably.

---

### `services.yaml` — Stable network endpoints

A `Service` gives pods a stable DNS name and IP address. Pods come and go (restarts, scaling), but the Service IP stays fixed.

```yaml
kind: Service
metadata:
  name: core
spec:
  selector:
    app: core    # Routes to any pod with label app=core
  ports:
    - port: 8080       # Port the Service listens on
      targetPort: 8080 # Port on the pod to forward to
```

When `api-gateway` calls `http://core:8080`, Kubernetes DNS resolves `core` to the Service's ClusterIP, and kube-proxy load-balances across all ready pods with `app: core`.

**Why `notification-service` has no Service:**  
It receives work via AMQP (RabbitMQ queue), not via HTTP calls. No other service calls it by DNS name. Creating a ClusterIP service for it would be meaningless — there are no callers.

---

### `ingress.yaml` — External entry point

```yaml
kind: Ingress
spec:
  ingressClassName: nginx  # minikube addon; GKE uses "gce"
  rules:
    - host: interview-hub.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 80
    - host: i-hub-be.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
```

The Ingress is implemented by an **Ingress Controller** — a pod running nginx (or GCE load balancer in production) that watches Ingress objects and programs itself accordingly. It's the only pod that listens on the cluster's external IP.

**Host-based routing:** The controller uses the HTTP `Host` header to decide which Service to forward to. A request for `interview-hub.local` goes to `frontend`; a request for `i-hub-be.local` goes to `api-gateway`.

**`/etc/hosts` mapping (local):**
```
127.0.0.1  interview-hub.local  i-hub-be.local
```
Your browser resolves these names to `127.0.0.1`, and `kubectl port-forward` tunnels that to the ingress controller inside minikube.

---

### `hpa.yaml` — Autoscaling

```yaml
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    kind: Deployment
    name: core
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70  # scale up when average CPU across all core pods > 70%
```

The HPA controller queries `metrics-server` every 15 seconds. If the average CPU across all `core` pods exceeds 70%, it increases `core`'s `replicas`. If CPU drops, it scales back down (with a 5-minute cooldown to avoid flapping).

**Why only `core`?** It's the stateful bottleneck — it runs JPA/Hibernate, handles auth, and calls both calendar-service and RabbitMQ. api-gateway is stateless and thin; calendar-service and notification-service have low, bursty load that doesn't warrant autoscaling.

**`resources.requests.cpu` is required for HPA:**  
HPA calculates utilization as `actual CPU / requested CPU`. Without a `requests.cpu` value in the pod spec, the HPA has no denominator and refuses to function. That's why `core` has explicit requests:
```yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
```

---

## 3. The Full Request Flow

### Case: User creates an interview

```
1. Browser  →  POST http://i-hub-be.local/api/interviews
               (Host: i-hub-be.local → Ingress routes to api-gateway Service)

2. api-gateway Pod  →  validates JWT, strips auth, forwards to  →  core Service :8080
                       (HTTP, using CORE_URL=http://core:8080 env var — K8s DNS)

3. core Pod  →  persists Interview to Supabase PostgreSQL (external, JDBC over TLS)
            →  POST to calendar-service via FeignClient
                (CALENDAR_SERVICE_URL=http://calendar-service:8082 — K8s DNS)
            →  publishes EmailMessage to RabbitMQ exchange
                (RABBITMQ_URL=amqp://user:pass@rabbitmq:5672 — K8s DNS for rabbitmq Service)

4. calendar-service Pod  →  calls Google Calendar API (external HTTPS)
                         →  returns event ID to core

5. notification-service Pod  →  consumes from RabbitMQ queue (Spring Cloud Stream)
                             →  calls Resend API (external HTTPS) to send email

6. core  →  returns 201 Created to api-gateway
7. api-gateway  →  returns 201 to browser
```

Steps 4 and 5 happen **concurrently** from core's perspective — core doesn't wait for the email to be sent. The calendar call is synchronous (FeignClient HTTP); the notification is async (AMQP).

---

## 4. Secret Flow Into Pods

```
secrets.yaml  ──(kubectl apply)──►  etcd (encrypted at rest in GKE)
                                         │
                                         │  kubelet mounts at pod startup
                                         ▼
                              Pod environment variables
                              (DB_URL, DB_PASSWORD, etc.)
```

Kubernetes injects secrets as environment variables **before the container process starts**. The Spring Boot application reads them via `${DB_URL}` in `application.yml` — it has no idea they came from a Kubernetes Secret vs a regular env var.

**Local vs. GKE secret flow:**

| Layer | Local (minikube) | GKE (production) |
|-------|-----------------|------------------|
| Source | `k8s/secrets.yaml` applied manually | GCP Secret Manager |
| Mechanism | `Secret` object → env vars via `envFrom` | CSI driver mounts → `SecretProviderClass` syncs to `Secret` → env vars |
| Auth to read | None (local cluster) | Workload Identity: KSA ↔ GCP SA IAM binding |
| Rotation | Manual re-apply | GCP Secret Manager versioning |

---

## 5. GKE Delta — What Changes for Production

When issue #96 is executed, these are the targeted changes to the local manifests:

| File | Local | GKE |
|------|-------|-----|
| `deployments.yaml` | `imagePullPolicy: Never`, local image names | `IfNotPresent`, Artifact Registry paths (`northamerica-northeast1-docker.pkg.dev/...`) |
| `ingress.yaml` | `ingressClassName: nginx`, `.local` hosts | `ingressClassName: gce`, real domains, static IP + managed cert annotations |
| `service-accounts.yaml` | No annotations | `iam.gke.io/gcp-service-account: <gsa>@<project>.iam.gserviceaccount.com` per SA |
| `secrets.yaml` | Plain `Secret`, applied manually | **Deleted** — replaced by `secret-provider-classes.yaml` (CSI driver) |
| `rabbitmq` | In-cluster Deployment + Service | Same — in-cluster RabbitMQ, no change needed |

The routing rules, probe config, HPA, Services, and namespace are **identical** in both environments.

---

## 6. Useful `kubectl` Commands

```bash
# See everything in the namespace
kubectl get all -n interview-hub-ns1

# Watch pod status in real time
kubectl get pods -n interview-hub-ns1 -w

# Stream logs from a running pod
kubectl logs -n interview-hub-ns1 deployment/core -f

# Check why a pod is crashing
kubectl describe pod -n interview-hub-ns1 -l app=core
kubectl logs -n interview-hub-ns1 deployment/core --previous

# Check HPA state
kubectl get hpa -n interview-hub-ns1

# CPU/memory metrics (requires metrics-server)
kubectl top pods -n interview-hub-ns1

# Port-forward RabbitMQ management UI (not exposed as a Service)
kubectl port-forward -n interview-hub-ns1 svc/rabbitmq 15672:15672
# → http://localhost:15672

# Get a shell inside a pod
kubectl exec -it -n interview-hub-ns1 deployment/core -- /bin/sh
```
