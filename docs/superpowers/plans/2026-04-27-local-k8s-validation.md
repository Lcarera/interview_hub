# Local K8s Validation (minikube) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create and close all local K8s validation GitHub issues, verify the full stack in docker-compose, write GCP-free K8s manifests, and validate end-to-end routing on minikube.

**Architecture:** Four new GitHub issues (parent epic + 3 subs) gate all existing GKE tickets (#94–#99). Local manifests live in `interview_hub/k8s/` and use plain K8s Secrets and nginx Ingress instead of GCP-specific layers (SecretProviderClass, GCE Ingress). Sensitive values in `k8s/secrets.yaml` (gitignored); structure documented in `k8s/secrets.example.yaml` (committed).

**Tech Stack:** minikube, kubectl, Spring Boot 4 (Buildpacks), Angular/nginx, `gh` CLI, CloudAMQP (external RabbitMQ).

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `k8s/namespace.yaml` | Create | Namespace `interview-hub-ns1` |
| `k8s/service-accounts.yaml` | Create | 5 KSAs, no Workload Identity annotations |
| `k8s/secrets.example.yaml` | Create | Secret structure with placeholder values (committed) |
| `k8s/secrets.yaml` | Create (gitignored) | Actual secrets for local dev |
| `k8s/deployments.yaml` | Create | 5 Deployments with health probes |
| `k8s/services.yaml` | Create | 4 ClusterIP services |
| `k8s/ingress.yaml` | Create | Host-based routing via nginx (minikube addon) |
| `k8s/hpa.yaml` | Create | HPA on core, CPU 70%, 1–5 replicas |
| `.gitignore` | Modify | Add `k8s/secrets.yaml` |

---

## Task 1: Cleanup and GitHub issue setup

**Files:** GitHub only (no code changes, except deleting local branch)

- [ ] **Step 1: Delete the local feat/microservices-plan3 branch (already merged)**

```bash
git branch -d feat/microservices-plan3
```
Expected: `Deleted branch feat/microservices-plan3 (was ...)`

- [ ] **Step 2: Create the parent epic**

```bash
gh issue create \
  --title "[CHORE] Local K8s validation (minikube)" \
  --label "chore" \
  --body "$(cat <<'EOF'
## Task Description

Validate that all 5 Interview Hub services run correctly on minikube using plain Kubernetes primitives before introducing any GCP-specific infrastructure (Workload Identity, SecretProviderClass, Terraform).

## Why

All open GKE migration tickets (#94–#99) mix Kubernetes concepts with GCP-specific layers. Working on both simultaneously makes it hard to learn Kubernetes. This epic produces local validation first so that GCP migration tickets start from a known-good K8s foundation.

## Sub-issues

- [ ] Sub 1: Verify all 5 services in docker-compose
- [ ] Sub 2: Write local K8s manifests (no GCP deps)
- [ ] Sub 3: Deploy to minikube and validate end-to-end

## Blocks

#94, #95, #96, #97, #98, #99 — all blocked until this epic closes.

## Acceptance criteria

- [ ] All 5 services run on minikube with no GCP dependencies
- [ ] Routing chain validated: browser → ingress → frontend and browser → ingress → api-gateway → core → calendar-service
- [ ] RabbitMQ messages reach notification-service (interview creation sends email)
- [ ] HPA is present and metrics-server reports CPU metrics for core
EOF
)"
```

Note the issue number printed — call it `$EPIC`. Use it in the next steps.

- [ ] **Step 3: Create Sub 1 issue (docker-compose smoke test)**

Replace `$EPIC` with the actual issue number from step 2.

```bash
gh issue create \
  --title "[CHORE] Verify all 5 services in docker-compose" \
  --label "chore" \
  --body "$(cat <<'EOF'
## Parent

#$EPIC

## Task Description

Confirm all 5 Interview Hub services start and pass health checks via docker-compose, establishing a known-good baseline before adding Kubernetes complexity.

## Why first

If a service fails in docker-compose, the problem is in application code or config — not K8s. If it passes, failures in later K8s tasks are definitively K8s problems.

## What to do

1. Build all backend service images: `./gradlew bootBuildImage`
2. Build frontend image: `docker build -t frontend:0.0.1-SNAPSHOT --build-arg NG_CONFIG=docker ./frontend`
3. Start: `docker compose up`
4. Verify health endpoints (see acceptance criteria)

## Acceptance criteria

- [ ] `docker compose up` starts all services without errors
- [ ] `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}` (api-gateway)
- [ ] `curl http://localhost:8082/actuator/health` returns `{"status":"UP"}` (calendar-service)
- [ ] `curl http://localhost` returns HTTP 200 (frontend)
- [ ] No `EUREKA_URL` references in compose.yaml

## Blocked by

#$EPIC (parent)
EOF
)"
```

- [ ] **Step 4: Create Sub 2 issue (local K8s manifests)**

```bash
gh issue create \
  --title "[CHORE] Write local K8s manifests (no GCP deps)" \
  --label "chore" \
  --body "$(cat <<'EOF'
## Parent

#$EPIC

## Task Description

Create `interview_hub/k8s/` with all Kubernetes manifest files using local-safe variants: plain Secrets instead of SecretProviderClass, nginx Ingress instead of GCE Ingress, no Workload Identity annotations.

## Files to create

| File | Local variant |
|------|--------------|
| `k8s/namespace.yaml` | Namespace `interview-hub-ns1` |
| `k8s/service-accounts.yaml` | 5 KSAs, no WI annotations |
| `k8s/secrets.example.yaml` | Placeholder values (committed) |
| `k8s/secrets.yaml` | Real values from .env (gitignored) |
| `k8s/deployments.yaml` | 5 Deployments, imagePullPolicy: Never |
| `k8s/services.yaml` | 4 ClusterIP services |
| `k8s/ingress.yaml` | ingressClassName: nginx, host-based routing |
| `k8s/hpa.yaml` | CPU-based HPA on core |

## GCP deltas (addressed later in #95/#96)

- `service-accounts.yaml`: add `iam.gke.io/gcp-service-account` annotations
- `secrets.yaml`: delete entirely (replaced by SecretProviderClass)
- `deployments.yaml`: swap image refs to Artifact Registry + change pull policy
- `ingress.yaml`: swap to `ingressClassName: gce` + static IP annotation

## Acceptance criteria

- [ ] `kubectl apply -f k8s/ --dry-run=client -n interview-hub-ns1` passes with no errors
- [ ] No GCP project IDs, Artifact Registry paths, or SecretProviderClass references in manifests

## Blocked by

Sub 1 (docker-compose verification)
EOF
)"
```

- [ ] **Step 5: Create Sub 3 issue (minikube deploy + validate)**

```bash
gh issue create \
  --title "[CHORE] Deploy to minikube and validate end-to-end" \
  --label "chore" \
  --body "$(cat <<'EOF'
## Parent

#$EPIC

## Task Description

Start minikube with ingress and metrics-server addons, load service images, apply manifests, and validate the full routing chain end-to-end.

## Acceptance criteria

- [ ] `kubectl get all -n interview-hub-ns1` shows 5 running Deployments and 4 Services
- [ ] `curl http://interview-hub.local` returns HTTP 200 (Angular SPA)
- [ ] `curl http://i-hub-be.local/actuator/health` returns `{"status":"UP"}`
- [ ] Creating an interview via the UI triggers a calendar event and sends a notification email
- [ ] `kubectl get hpa -n interview-hub-ns1` shows the HPA for core
- [ ] `kubectl top pods -n interview-hub-ns1` returns CPU/memory metrics

## Blocked by

Sub 2 (local K8s manifests)
EOF
)"
```

- [ ] **Step 6: Add blocked-by comment to each GKE ticket**

Replace `$EPIC` with the actual parent issue number.

```bash
for issue in 94 95 96 97 98 99; do
  gh issue comment $issue --body "**Blocked by:** Local K8s validation epic — #$EPIC must close before this ticket starts."
done
```

---

## Task 2: Sub 1 — docker-compose smoke test

**Files:** None (verification only)

**Prerequisite:** Check if there is a stash (`git stash list`). If the stash shows `Add Postman collection for API testing` on an older commit, leave it — do not pop it now.

- [ ] **Step 1: Build all backend images**

From the `interview_hub/` root:

```bash
./gradlew :services:api-gateway:bootBuildImage \
          :services:core:bootBuildImage \
          :services:calendar-service:bootBuildImage \
          :services:notification-service:bootBuildImage
```

Expected: each service prints `Successfully built image 'docker.io/library/<service>:0.0.1-SNAPSHOT'`

- [ ] **Step 2: Build the frontend image**

```bash
docker build -t frontend:0.0.1-SNAPSHOT --build-arg NG_CONFIG=docker ./frontend
```

Expected: `Successfully tagged frontend:0.0.1-SNAPSHOT`

- [ ] **Step 3: Start docker-compose**

```bash
docker compose up
```

Wait until all services show as healthy. RabbitMQ will start first; api-gateway, core, and calendar-service start after it.

- [ ] **Step 4: Verify health endpoints (new terminal)**

```bash
curl -s http://localhost:8080/actuator/health | grep -o '"status":"[^"]*"'
# Expected: "status":"UP"

curl -s http://localhost:8082/actuator/health | grep -o '"status":"[^"]*"'
# Expected: "status":"UP"

curl -o /dev/null -w "%{http_code}" http://localhost
# Expected: 200
```

- [ ] **Step 5: Stop docker-compose and close Sub 1 issue**

```bash
# Ctrl+C in the docker compose terminal, then:
docker compose down

gh issue close $SUB1_ISSUE --comment "All 5 services verified healthy in docker-compose."
```

---

## Task 3: Manifest scaffolding — namespace, service accounts, secrets

**Files:**
- Create: `k8s/namespace.yaml`
- Create: `k8s/service-accounts.yaml`
- Create: `k8s/secrets.example.yaml`
- Create: `k8s/secrets.yaml` (gitignored)
- Modify: `.gitignore`

- [ ] **Step 1: Add secrets.yaml to .gitignore**

Open `.gitignore` and add at the end:

```
k8s/secrets.yaml
```

- [ ] **Step 2: Create k8s/namespace.yaml**

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: interview-hub-ns1
```

- [ ] **Step 3: Create k8s/service-accounts.yaml**

No Workload Identity annotations — those are GCP-specific and added in #95.

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: gateway-sa
  namespace: interview-hub-ns1
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: backend-sa
  namespace: interview-hub-ns1
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: calendar-sa
  namespace: interview-hub-ns1
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: notification-sa
  namespace: interview-hub-ns1
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: frontend-sa
  namespace: interview-hub-ns1
```

- [ ] **Step 4: Create k8s/secrets.example.yaml (committed — shows structure, no real values)**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: gateway-secret
  namespace: interview-hub-ns1
type: Opaque
stringData:
  JWT_SIGNING_SECRET: "<your-jwt-signing-secret>"
---
apiVersion: v1
kind: Secret
metadata:
  name: core-secret
  namespace: interview-hub-ns1
type: Opaque
stringData:
  DB_URL: "<jdbc:postgresql://...>"
  DB_USERNAME: "<db-username>"
  DB_PASSWORD: "<db-password>"
  GOOGLE_CLIENT_ID: "<google-client-id>"
  GOOGLE_CLIENT_SECRET: "<google-client-secret>"
  JWT_SIGNING_SECRET: "<your-jwt-signing-secret>"
  RABBITMQ_URL: "<amqps://...@cloudamqp-host/vhost>"
---
apiVersion: v1
kind: Secret
metadata:
  name: calendar-secret
  namespace: interview-hub-ns1
type: Opaque
stringData:
  GOOGLE_CLIENT_ID: "<google-client-id>"
  GOOGLE_CLIENT_SECRET: "<google-client-secret>"
  GOOGLE_CALENDAR_ID: "<calendar-id-or-primary>"
  GOOGLE_CALENDAR_REFRESH_TOKEN: "<refresh-token>"
---
apiVersion: v1
kind: Secret
metadata:
  name: notification-secret
  namespace: interview-hub-ns1
type: Opaque
stringData:
  RESEND_API_KEY: "<resend-api-key>"
  RABBITMQ_URL: "<amqps://...@cloudamqp-host/vhost>"
  MAIL_FROM: "<noreply@lcarera.dev>"
  FRONTEND_URL: "http://interview-hub.local"
```

- [ ] **Step 5: Create k8s/secrets.yaml (local only — fill in real values from .env)**

Copy the example and fill in real values:

```bash
cp k8s/secrets.example.yaml k8s/secrets.yaml
```

Then open `k8s/secrets.yaml` and replace every `<...>` placeholder with the real value from your `.env` file.

**Important — RABBITMQ_URL in secrets.yaml:** Use the CloudAMQP URL (e.g. `amqps://user:pass@host/vhost`), **not** `amqp://guest:guest@rabbitmq:5672`. The local RabbitMQ Docker container is not part of the minikube cluster.

**Important — GOOGLE_CALENDAR_ID:** Use `primary` if you haven't set up a dedicated calendar.

- [ ] **Step 6: Dry-run namespace and service accounts**

```bash
kubectl apply -f k8s/namespace.yaml --dry-run=client
# Expected: namespace/interview-hub-ns1 configured (dry run)

kubectl apply -f k8s/service-accounts.yaml --dry-run=client -n interview-hub-ns1
# Expected: serviceaccount/gateway-sa configured (dry run) × 5
```

- [ ] **Step 7: Commit**

```bash
git add k8s/namespace.yaml k8s/service-accounts.yaml k8s/secrets.example.yaml .gitignore
git commit -m "chore: add k8s namespace, service accounts, and secrets structure"
```

---

## Task 4: Deployments, services, ingress, and HPA

**Files:**
- Create: `k8s/deployments.yaml`
- Create: `k8s/services.yaml`
- Create: `k8s/ingress.yaml`
- Create: `k8s/hpa.yaml`

### Port reference

| Service | Container port | Notes |
|---------|---------------|-------|
| api-gateway | 8080 | Defined in application.yml |
| core | 8080 | Spring Boot default (no server.port override in K8s) |
| calendar-service | 8082 | Defined in application.yml |
| notification-service | 8083 | Defined in application.yml |
| frontend | 80 | nginx |

- [ ] **Step 1: Create k8s/deployments.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: interview-hub-ns1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      serviceAccountName: gateway-sa
      containers:
        - name: api-gateway
          image: api-gateway:0.0.1-SNAPSHOT
          imagePullPolicy: Never
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: gateway-secret
          env:
            - name: CORE_URL
              value: "http://core:8080"
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 40
            periodSeconds: 15
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: core
  namespace: interview-hub-ns1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: core
  template:
    metadata:
      labels:
        app: core
    spec:
      serviceAccountName: backend-sa
      containers:
        - name: core
          image: interview-hub:0.0.1-SNAPSHOT
          imagePullPolicy: Never
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: core-secret
          env:
            - name: SERVER_PORT
              value: "8080"
            - name: APP_BASE_URL
              value: "http://i-hub-be.local"
            - name: FRONTEND_URL
              value: "http://interview-hub.local"
            - name: CALENDAR_SERVICE_URL
              value: "http://calendar-service:8082"
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 15
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: calendar-service
  namespace: interview-hub-ns1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: calendar-service
  template:
    metadata:
      labels:
        app: calendar-service
    spec:
      serviceAccountName: calendar-sa
      containers:
        - name: calendar-service
          image: calendar-service:0.0.1-SNAPSHOT
          imagePullPolicy: Never
          ports:
            - containerPort: 8082
          envFrom:
            - secretRef:
                name: calendar-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8082
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8082
            initialDelaySeconds: 40
            periodSeconds: 15
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
  namespace: interview-hub-ns1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      serviceAccountName: notification-sa
      containers:
        - name: notification-service
          image: notification-service:0.0.1-SNAPSHOT
          imagePullPolicy: Never
          ports:
            - containerPort: 8083
          envFrom:
            - secretRef:
                name: notification-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8083
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8083
            initialDelaySeconds: 40
            periodSeconds: 15
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: interview-hub-ns1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      serviceAccountName: frontend-sa
      containers:
        - name: frontend
          image: frontend:0.0.1-SNAPSHOT
          imagePullPolicy: Never
          ports:
            - containerPort: 80
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 10
```

- [ ] **Step 2: Create k8s/services.yaml**

notification-service has no ClusterIP service — it receives messages via AMQP, not HTTP.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: interview-hub-ns1
spec:
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: core
  namespace: interview-hub-ns1
spec:
  selector:
    app: core
  ports:
    - port: 8080
      targetPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: calendar-service
  namespace: interview-hub-ns1
spec:
  selector:
    app: calendar-service
  ports:
    - port: 8082
      targetPort: 8082
---
apiVersion: v1
kind: Service
metadata:
  name: frontend
  namespace: interview-hub-ns1
spec:
  selector:
    app: frontend
  ports:
    - port: 80
      targetPort: 80
```

- [ ] **Step 3: Create k8s/ingress.yaml**

`ingressClassName: nginx` — uses the minikube nginx addon. For GKE this becomes `ingressClassName: gce` + static IP annotation (changed in #96).

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: interview-hub-ingress
  namespace: interview-hub-ns1
spec:
  ingressClassName: nginx
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

- [ ] **Step 4: Create k8s/hpa.yaml**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: core-hpa
  namespace: interview-hub-ns1
spec:
  scaleTargetRef:
    apiVersion: apps/v1
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
          averageUtilization: 70
```

- [ ] **Step 5: Dry-run all manifests**

```bash
kubectl apply -f k8s/ --dry-run=client -n interview-hub-ns1
```

Expected output (one line per resource, all with `(dry run)`):
```
namespace/interview-hub-ns1 configured (dry run)
serviceaccount/gateway-sa configured (dry run)
serviceaccount/backend-sa configured (dry run)
serviceaccount/calendar-sa configured (dry run)
serviceaccount/notification-sa configured (dry run)
serviceaccount/frontend-sa configured (dry run)
secret/gateway-secret configured (dry run)
secret/core-secret configured (dry run)
secret/calendar-secret configured (dry run)
secret/notification-secret configured (dry run)
deployment.apps/api-gateway configured (dry run)
deployment.apps/core configured (dry run)
deployment.apps/calendar-service configured (dry run)
deployment.apps/notification-service configured (dry run)
deployment.apps/frontend configured (dry run)
service/api-gateway configured (dry run)
service/core configured (dry run)
service/calendar-service configured (dry run)
service/frontend configured (dry run)
ingress.networking.k8s.io/interview-hub-ingress configured (dry run)
horizontalpodautoscaler.autoscaling/core-hpa configured (dry run)
```

If `secrets.yaml` is missing (gitignored), the dry-run will still pass for the other files. The secrets must be applied separately in Task 5.

- [ ] **Step 6: Commit**

```bash
git add k8s/deployments.yaml k8s/services.yaml k8s/ingress.yaml k8s/hpa.yaml
git commit -m "chore: add k8s deployments, services, ingress, and HPA for minikube"
```

---

## Task 5: Deploy to minikube and validate

**Files:** None (operational steps only)

- [ ] **Step 1: Start minikube with required addons**

```bash
minikube start
minikube addons enable ingress
minikube addons enable metrics-server
```

Wait for addons to be ready:
```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

Expected: `pod/ingress-nginx-controller-... condition met`

- [ ] **Step 2: Point Docker at minikube's daemon and build images**

Building inside minikube's Docker context means images are immediately available with `imagePullPolicy: Never` — no `minikube image load` needed.

```bash
eval $(minikube docker-env)

./gradlew :services:api-gateway:bootBuildImage \
          :services:core:bootBuildImage \
          :services:calendar-service:bootBuildImage \
          :services:notification-service:bootBuildImage

docker build -t frontend:0.0.1-SNAPSHOT --build-arg NG_CONFIG=docker ./frontend
```

Verify images are present in minikube's daemon:
```bash
docker images | grep -E "api-gateway|interview-hub|calendar-service|notification-service|frontend"
```

Expected: 5 rows with tag `0.0.1-SNAPSHOT`.

- [ ] **Step 3: Apply namespace first (it must exist before namespaced resources)**

```bash
kubectl apply -f k8s/namespace.yaml
```

Expected: `namespace/interview-hub-ns1 created`

- [ ] **Step 4: Apply secrets (real values, local file only)**

```bash
kubectl apply -f k8s/secrets.yaml
```

Expected:
```
secret/gateway-secret created
secret/core-secret created
secret/calendar-secret created
secret/notification-secret created
```

If you see `Error: secrets.yaml not found`, you skipped Task 3 Step 5 — copy `secrets.example.yaml` to `secrets.yaml` and fill in real values.

- [ ] **Step 5: Apply all remaining manifests**

```bash
kubectl apply -f k8s/service-accounts.yaml \
              -f k8s/deployments.yaml \
              -f k8s/services.yaml \
              -f k8s/ingress.yaml \
              -f k8s/hpa.yaml
```

- [ ] **Step 6: Watch deployments roll out**

```bash
kubectl rollout status deployment/api-gateway -n interview-hub-ns1 --timeout=3m
kubectl rollout status deployment/core -n interview-hub-ns1 --timeout=3m
kubectl rollout status deployment/calendar-service -n interview-hub-ns1 --timeout=3m
kubectl rollout status deployment/notification-service -n interview-hub-ns1 --timeout=3m
kubectl rollout status deployment/frontend -n interview-hub-ns1 --timeout=3m
```

Expected: `deployment "<name>" successfully rolled out` × 5

If a deployment fails, check logs:
```bash
kubectl logs -n interview-hub-ns1 deployment/<name> --previous
kubectl describe pod -n interview-hub-ns1 -l app=<name>
```

- [ ] **Step 7: Update /etc/hosts**

Get the minikube IP and add both hostnames:

```bash
echo "$(minikube ip)  interview-hub.local  i-hub-be.local" | sudo tee -a /etc/hosts
```

Verify:
```bash
grep "interview-hub.local" /etc/hosts
# Expected: <minikube-ip>  interview-hub.local  i-hub-be.local
```

- [ ] **Step 8: Verify the full stack**

```bash
# api-gateway health
curl -s http://i-hub-be.local/actuator/health
# Expected: {"status":"UP"}

# Frontend loads
curl -o /dev/null -w "%{http_code}" http://interview-hub.local
# Expected: 200
```

Open `http://interview-hub.local` in a browser — the Interview Hub login page should load.

Log in with a `@gm2dev.com` account and create an interview. Verify:
- A Google Calendar event is created (calendar-service received the FeignClient call from core)
- A notification email arrives (notification-service consumed the RabbitMQ message)

- [ ] **Step 9: Verify HPA and metrics**

```bash
kubectl get hpa -n interview-hub-ns1
# Expected: NAME       REFERENCE          TARGETS   MINPODS   MAXPODS   REPLICAS
#           core-hpa   Deployment/core    <x>%/70%  1         5         1

kubectl top pods -n interview-hub-ns1
# Expected: one row per pod with CPU and MEMORY columns populated
```

If `kubectl top pods` returns `error: Metrics API not available`, wait 60 seconds — metrics-server takes time to collect its first data point.

- [ ] **Step 10: Verify all resources**

```bash
kubectl get all -n interview-hub-ns1
```

Expected: 5 Deployments (1/1 ready), 5 ReplicaSets, 5 Pods (Running), 4 Services, 1 HPA, 1 Ingress.

- [ ] **Step 11: Close Sub 2 and Sub 3 issues, then close the parent epic**

```bash
gh issue close $SUB2_ISSUE --comment "All k8s/ manifests written and dry-run validated."
gh issue close $SUB3_ISSUE --comment "All 5 services running on minikube. Full routing chain and HPA verified."
gh issue close $EPIC --comment "Local K8s validation complete. All sub-issues closed. GKE migration tickets (#94–#99) are now unblocked."
```

- [ ] **Step 12: Push commits to origin**

```bash
git push origin main
```

Expected: commits for the spec doc, plan doc, and k8s manifests pushed.

---

## After this plan

The next step is the GKE migration, starting with **#94** (reserve static IP + Kubernetes provider in Terraform). At the start of #94, update `k8s/ingress.yaml` to add the GCE class and static IP annotation. At the start of #96, update `k8s/service-accounts.yaml` with Workload Identity annotations and replace `k8s/secrets.yaml` with `k8s/secret-provider-classes.yaml`.

The `k8s/` manifests written here are the canonical source. GCP migration = targeted substitutions, not a rewrite.
