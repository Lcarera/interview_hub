# Pulumi GCP Deployment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deploy Interview Hub to GCP using Pulumi (Python) with Cloud Run, Artifact Registry, Secret Manager, and a global HTTPS Load Balancer.

**Architecture:** Two private Cloud Run services (backend + frontend) sit behind a Global External HTTPS Load Balancer. A URL map routes API paths (`/auth/*`, `/interviews`, `/shadowing-requests`, `/actuator`) to the backend service and everything else to the frontend. Secrets are stored in GCP Secret Manager and injected as env vars by Cloud Run natively.

**Tech Stack:** Pulumi Python 3.x, `pulumi-gcp` v7, GCP Cloud Run v2, Artifact Registry, Secret Manager, Compute Engine (global LB), GitHub Actions.

---

## Prerequisites (run once manually before any task)

Enable required GCP APIs. Replace `PROJECT_ID` with your actual GCP project ID:

```bash
gcloud config set project PROJECT_ID

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  compute.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com
```

Install Pulumi CLI (if not already installed):
```bash
# macOS/Linux
curl -fsSL https://get.pulumi.com | sh
# Windows (PowerShell)
choco install pulumi
```

Log in to Pulumi Cloud (free tier for state backend):
```bash
pulumi login
```

---

## Task 1: Bootstrap the Pulumi Python Project

**Files:**
- Create: `infra/Pulumi.yaml`
- Create: `infra/requirements.txt`
- Create: `infra/__main__.py`
- Create: `infra/Pulumi.prod.yaml`

**Step 1: Create the infra directory and Pulumi project file**

```bash
mkdir infra
```

Create `infra/Pulumi.yaml`:
```yaml
name: interview-hub-infra
runtime:
  name: python
  options:
    virtualenv: venv
description: Interview Hub GCP infrastructure
```

**Step 2: Create requirements.txt**

Create `infra/requirements.txt`:
```
pulumi>=3.0.0,<4.0.0
pulumi-gcp>=7.0.0,<8.0.0
```

**Step 3: Create the Python virtual environment and install deps**

```bash
cd infra
python -m venv venv
# On Linux/macOS:
source venv/bin/activate
# On Windows (bash):
source venv/Scripts/activate

pip install -r requirements.txt
```

**Step 4: Create the skeleton __main__.py**

Create `infra/__main__.py`:
```python
import pulumi

# Components are imported here as they are built
# pulumi.export() calls go at the bottom
```

**Step 5: Create the stack config file**

Create `infra/Pulumi.prod.yaml` (replace placeholder values):
```yaml
config:
  gcp:project: YOUR_GCP_PROJECT_ID
  gcp:region: us-central1
  interview-hub-infra:domain: yourdomain.com
```

**Step 6: Initialize the Pulumi stack**

```bash
cd infra
pulumi stack init prod
```

**Step 7: Run preview to verify the project bootstraps cleanly**

```bash
cd infra
pulumi preview
```

Expected output:
```
Resources:
    + 0 to create
```
(No resources yet — that's correct.)

**Step 8: Commit**

```bash
git add infra/
git commit -m "feat: bootstrap Pulumi infra project"
```

---

## Task 2: Artifact Registry

**Files:**
- Create: `infra/registry.py`
- Modify: `infra/__main__.py`

**Step 1: Create registry.py**

Create `infra/registry.py`:
```python
import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")
region = gcp_config.require("region")

repo = gcp.artifactregistry.Repository(
    "interview-hub-repo",
    repository_id="interview-hub",
    location=region,
    format="DOCKER",
    description="Interview Hub Docker images",
)

registry_url = pulumi.Output.concat(
    region, "-docker.pkg.dev/", project, "/interview-hub"
)
```

**Step 2: Wire into __main__.py**

Replace `infra/__main__.py` with:
```python
import pulumi
from registry import registry_url

pulumi.export("registry_url", registry_url)
```

**Step 3: Preview — expect 1 new resource**

```bash
cd infra
pulumi preview
```

Expected output:
```
+ google-native:artifactregistry/v1:Repository  interview-hub-repo  create
```
(Exact resource type may vary — confirm it shows 1 repository to create.)

**Step 4: Commit**

```bash
git add infra/registry.py infra/__main__.py
git commit -m "feat(infra): add Artifact Registry"
```

---

## Task 3: IAM — Service Accounts and Role Bindings

**Files:**
- Create: `infra/iam.py`
- Modify: `infra/__main__.py`

**Step 1: Create iam.py**

Create `infra/iam.py`:
```python
import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

# Service account used by the Cloud Run backend container at runtime
cloudrun_sa = gcp.serviceaccount.Account(
    "cloudrun-sa",
    account_id="interview-hub-cloudrun",
    display_name="Interview Hub Cloud Run Service Account",
    project=project,
)

# Allow the service account to read secrets from Secret Manager
secret_access_binding = gcp.projects.IAMMember(
    "cloudrun-secret-access",
    project=project,
    role="roles/secretmanager.secretAccessor",
    member=pulumi.Output.concat("serviceAccount:", cloudrun_sa.email),
)

# Allow the global LB to invoke both Cloud Run services (set on individual
# services in cloudrun.py using allUsers — ingress setting keeps them private
# from the direct internet)
```

**Step 2: Wire into __main__.py**

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects

pulumi.export("registry_url", registry_url)
```

**Step 3: Preview — expect 2 new resources**

```bash
cd infra
pulumi preview
```

Expected: service account + 1 IAM binding to create.

**Step 4: Commit**

```bash
git add infra/iam.py infra/__main__.py
git commit -m "feat(infra): add Cloud Run service account and IAM bindings"
```

---

## Task 4: Secret Manager Secrets

**Files:**
- Create: `infra/secrets.py`
- Modify: `infra/__main__.py`

**Step 1: Create secrets.py**

Create `infra/secrets.py`:
```python
import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

# These are the 7 env vars the backend needs (values set manually via gcloud)
_SECRET_NAMES = [
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "GOOGLE_CLIENT_ID",
    "GOOGLE_CLIENT_SECRET",
    "JWT_SIGNING_SECRET",
    "TOKEN_ENCRYPTION_KEY",
]

secrets: dict[str, gcp.secretmanager.Secret] = {}
for _name in _SECRET_NAMES:
    _resource_id = _name.lower().replace("_", "-")
    secrets[_name] = gcp.secretmanager.Secret(
        f"secret-{_resource_id}",
        secret_id=f"interview-hub-{_resource_id}",
        project=project,
        replication=gcp.secretmanager.SecretReplicationArgs(
            auto=gcp.secretmanager.SecretReplicationAutoArgs(),
        ),
    )
```

**Step 2: Wire into __main__.py**

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401
from secrets import secrets  # noqa: F401

pulumi.export("registry_url", registry_url)
```

**Step 3: Preview — expect 7 new Secret Manager secrets**

```bash
cd infra
pulumi preview
```

Expected: 7 secrets to create.

**Step 4: Commit**

```bash
git add infra/secrets.py infra/__main__.py
git commit -m "feat(infra): add Secret Manager secrets"
```

**Step 5: After first `pulumi up` (Task 7), seed the secret values**

These commands set the actual values. Run them once after deploying:
```bash
echo -n "your-db-url" | gcloud secrets versions add interview-hub-db-url --data-file=-
echo -n "your-db-username" | gcloud secrets versions add interview-hub-db-username --data-file=-
echo -n "your-db-password" | gcloud secrets versions add interview-hub-db-password --data-file=-
echo -n "your-google-client-id" | gcloud secrets versions add interview-hub-google-client-id --data-file=-
echo -n "your-google-client-secret" | gcloud secrets versions add interview-hub-google-client-secret --data-file=-
echo -n "your-jwt-signing-secret" | gcloud secrets versions add interview-hub-jwt-signing-secret --data-file=-
echo -n "your-token-encryption-key" | gcloud secrets versions add interview-hub-token-encryption-key --data-file=-
```

---

## Task 5: Cloud Run Services

**Files:**
- Create: `infra/cloudrun.py`
- Modify: `infra/__main__.py`

**Step 1: Create cloudrun.py**

Create `infra/cloudrun.py`:
```python
import pulumi
import pulumi_gcp as gcp
from iam import cloudrun_sa
from secrets import secrets

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Image URIs are set at deploy time via CI (or Pulumi config for manual deploys)
backend_image = config.get("backend_image") or "gcr.io/cloudrun/hello"
frontend_image = config.get("frontend_image") or "gcr.io/cloudrun/hello"

# Build secret env var list from Secret Manager secrets
_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name=name,
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secret.secret_id,
                version="latest",
            )
        ),
    )
    for name, secret in secrets.items()
]

backend_service = gcp.cloudrunv2.Service(
    "backend",
    name="interview-hub-backend",
    location=region,
    project=project,
    # Only reachable via the global LB — not directly from the internet
    ingress="INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER",
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=backend_image,
                ports=[
                    gcp.cloudrunv2.ServiceTemplateContainerPortArgs(
                        container_port=8080
                    )
                ],
                envs=_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="APP_BASE_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="FRONTEND_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                ],
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8080,
                    ),
                    initial_delay_seconds=10,
                    period_seconds=5,
                    failure_threshold=12,
                ),
            )
        ],
    ),
)

# Allow the global LB to invoke the backend (ingress setting prevents direct
# internet access; allUsers here only allows the LB path)
gcp.cloudrunv2.ServiceIamMember(
    "backend-invoker",
    project=project,
    location=region,
    name=backend_service.name,
    role="roles/run.invoker",
    member="allUsers",
)

frontend_service = gcp.cloudrunv2.Service(
    "frontend",
    name="interview-hub-frontend",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER",
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=frontend_image,
                ports=[
                    gcp.cloudrunv2.ServiceTemplateContainerPortArgs(
                        container_port=80
                    )
                ],
            )
        ],
    ),
)

gcp.cloudrunv2.ServiceIamMember(
    "frontend-invoker",
    project=project,
    location=region,
    name=frontend_service.name,
    role="roles/run.invoker",
    member="allUsers",
)
```

**Step 2: Wire into __main__.py**

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401
from secrets import secrets  # noqa: F401
from cloudrun import backend_service, frontend_service

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
```

**Step 3: Preview — expect 4 new resources (2 services + 2 IAM bindings)**

```bash
cd infra
pulumi preview
```

**Step 4: Commit**

```bash
git add infra/cloudrun.py infra/__main__.py
git commit -m "feat(infra): add Cloud Run services for backend and frontend"
```

---

## Task 6: Load Balancer

**Files:**
- Create: `infra/loadbalancer.py`
- Modify: `infra/__main__.py`

**Step 1: Create loadbalancer.py**

Create `infra/loadbalancer.py`:
```python
import pulumi
import pulumi_gcp as gcp
from cloudrun import backend_service, frontend_service

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Static external IP
static_ip = gcp.compute.GlobalAddress(
    "interview-hub-ip",
    name="interview-hub-ip",
    project=project,
)

# Managed SSL certificate (auto-provisioned by GCP after DNS is pointed at the IP)
ssl_cert = gcp.compute.ManagedSslCertificate(
    "interview-hub-cert",
    name="interview-hub-cert",
    project=project,
    managed=gcp.compute.ManagedSslCertificateManagedArgs(
        domains=[domain],
    ),
)

# Serverless NEG for backend Cloud Run service
backend_neg = gcp.compute.RegionNetworkEndpointGroup(
    "backend-neg",
    name="interview-hub-backend-neg",
    network_endpoint_type="SERVERLESS",
    region=region,
    project=project,
    cloud_run=gcp.compute.RegionNetworkEndpointGroupCloudRunArgs(
        service=backend_service.name,
    ),
)

# Serverless NEG for frontend Cloud Run service
frontend_neg = gcp.compute.RegionNetworkEndpointGroup(
    "frontend-neg",
    name="interview-hub-frontend-neg",
    network_endpoint_type="SERVERLESS",
    region=region,
    project=project,
    cloud_run=gcp.compute.RegionNetworkEndpointGroupCloudRunArgs(
        service=frontend_service.name,
    ),
)

# Backend services (global LB components pointing to the NEGs)
# Note: health_checks are NOT supported for serverless NEGs — omit them
backend_lb_service = gcp.compute.BackendService(
    "backend-lb-service",
    name="interview-hub-backend-service",
    project=project,
    protocol="HTTP",
    load_balancing_scheme="EXTERNAL_MANAGED",
    backends=[
        gcp.compute.BackendServiceBackendArgs(
            group=backend_neg.id,
        )
    ],
)

frontend_lb_service = gcp.compute.BackendService(
    "frontend-lb-service",
    name="interview-hub-frontend-service",
    project=project,
    protocol="HTTP",
    load_balancing_scheme="EXTERNAL_MANAGED",
    backends=[
        gcp.compute.BackendServiceBackendArgs(
            group=frontend_neg.id,
        )
    ],
)

# URL map: route API paths to backend, everything else to frontend
url_map = gcp.compute.URLMap(
    "url-map",
    name="interview-hub-url-map",
    project=project,
    default_service=frontend_lb_service.id,
    host_rules=[
        gcp.compute.URLMapHostRuleArgs(
            hosts=["*"],
            path_matcher="allpaths",
        )
    ],
    path_matchers=[
        gcp.compute.URLMapPathMatcherArgs(
            name="allpaths",
            default_service=frontend_lb_service.id,
            path_rules=[
                gcp.compute.URLMapPathMatcherPathRuleArgs(
                    paths=[
                        "/auth/google",
                        "/auth/google/*",
                        "/auth/token",
                        "/interviews",
                        "/interviews/*",
                        "/shadowing-requests",
                        "/shadowing-requests/*",
                        "/actuator",
                        "/actuator/*",
                    ],
                    service=backend_lb_service.id,
                )
            ],
        )
    ],
)

# HTTPS proxy (terminates TLS, uses SSL cert)
https_proxy = gcp.compute.TargetHttpsProxy(
    "https-proxy",
    name="interview-hub-https-proxy",
    project=project,
    url_map=url_map.id,
    ssl_certificates=[ssl_cert.id],
)

# Forwarding rule: binds static IP + port 443 to the HTTPS proxy
forwarding_rule = gcp.compute.GlobalForwardingRule(
    "forwarding-rule",
    name="interview-hub-forwarding-rule",
    project=project,
    target=https_proxy.id,
    port_range="443",
    ip_address=static_ip.address,
    load_balancing_scheme="EXTERNAL_MANAGED",
)
```

**Step 2: Wire into __main__.py**

```python
import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401
from secrets import secrets  # noqa: F401
from cloudrun import backend_service, frontend_service
from loadbalancer import static_ip

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
pulumi.export("load_balancer_ip", static_ip.address)
```

**Step 3: Preview — expect ~8 new LB resources**

```bash
cd infra
pulumi preview
```

Expected resources: GlobalAddress, ManagedSslCertificate, 2× RegionNetworkEndpointGroup, 2× BackendService, URLMap, TargetHttpsProxy, GlobalForwardingRule.

**Step 4: Commit**

```bash
git add infra/loadbalancer.py infra/__main__.py
git commit -m "feat(infra): add global HTTPS load balancer with URL map"
```

---

## Task 7: First Deploy and DNS Setup

This task deploys all infrastructure for the first time and seeds secrets.

**Step 1: Build and push the initial images**

Tag and push the backend image (uses the `bootBuildImage` Gradle task):
```bash
# From repo root
REGISTRY=us-central1-docker.pkg.dev
PROJECT=YOUR_GCP_PROJECT_ID
SHA=$(git rev-parse --short HEAD)

# Authenticate Docker to Artifact Registry
gcloud auth configure-docker $REGISTRY

# First run pulumi up to create the registry, then push images
cd infra && pulumi up --yes --target "google-native:artifactregistry/v1:Repository::interview-hub-repo"
cd ..

# Build and push backend
./gradlew bootBuildImage --imageName=$REGISTRY/$PROJECT/interview-hub/backend:$SHA
docker push $REGISTRY/$PROJECT/interview-hub/backend:$SHA

# Build and push frontend
docker build -t $REGISTRY/$PROJECT/interview-hub/frontend:$SHA ./frontend
docker push $REGISTRY/$PROJECT/interview-hub/frontend:$SHA
```

**Step 2: Set image config in the Pulumi stack**

```bash
cd infra
pulumi config set interview-hub-infra:backend_image $REGISTRY/$PROJECT/interview-hub/backend:$SHA
pulumi config set interview-hub-infra:frontend_image $REGISTRY/$PROJECT/interview-hub/frontend:$SHA
```

**Step 3: Deploy everything**

```bash
cd infra
pulumi up --yes
```

Expected: ~20 resources created. Note the `load_balancer_ip` output value.

**Step 4: Seed secret values**

```bash
echo -n "jdbc:postgresql://..." | gcloud secrets versions add interview-hub-db-url --data-file=-
echo -n "your-username" | gcloud secrets versions add interview-hub-db-username --data-file=-
echo -n "your-password" | gcloud secrets versions add interview-hub-db-password --data-file=-
echo -n "your-google-client-id" | gcloud secrets versions add interview-hub-google-client-id --data-file=-
echo -n "your-google-client-secret" | gcloud secrets versions add interview-hub-google-client-secret --data-file=-
echo -n "your-jwt-secret-min-32-chars" | gcloud secrets versions add interview-hub-jwt-signing-secret --data-file=-
echo -n "your-aes-encryption-key" | gcloud secrets versions add interview-hub-token-encryption-key --data-file=-
```

**Step 5: Add Google OAuth redirect URI**

In the Google Cloud Console → APIs & Services → Credentials → your OAuth 2.0 Client:
- Add `https://yourdomain.com/auth/google/callback` to Authorized redirect URIs.

**Step 6: Point DNS to the static IP**

Create an A record:
```
yourdomain.com → <load_balancer_ip from pulumi output>
```

GCP will auto-provision the SSL cert once DNS propagates (can take 10-60 minutes).

**Step 7: Verify the backend health check**

```bash
curl https://yourdomain.com/actuator/health
```

Expected: `{"status":"UP"}`

**Step 8: Redeploy backend with secrets to pick them up**

After seeding secrets, Cloud Run needs a new revision to mount them:
```bash
cd infra
pulumi up --yes
```

**Step 9: Commit Pulumi stack config**

```bash
git add infra/Pulumi.prod.yaml
git commit -m "feat(infra): set production image tags in stack config"
```

---

## Task 8: GitHub Actions CI/CD

**Files:**
- Create: `.github/workflows/deploy.yml`

**Step 1: Create a CI service account in GCP**

```bash
PROJECT=YOUR_GCP_PROJECT_ID

gcloud iam service-accounts create interview-hub-ci \
  --display-name="Interview Hub CI/CD" \
  --project=$PROJECT

CI_SA="interview-hub-ci@$PROJECT.iam.gserviceaccount.com"

# Grant required roles
for ROLE in \
  roles/run.admin \
  roles/artifactregistry.writer \
  roles/secretmanager.admin \
  roles/compute.loadBalancerAdmin \
  roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding $PROJECT \
    --member="serviceAccount:$CI_SA" \
    --role="$ROLE"
done

# Create and download a key
gcloud iam service-accounts keys create /tmp/ci-sa-key.json \
  --iam-account=$CI_SA
```

**Step 2: Add GitHub repository secrets**

In GitHub → Settings → Secrets and variables → Actions, add:

| Secret name | Value |
|---|---|
| `GCP_SA_KEY` | Contents of `/tmp/ci-sa-key.json` |
| `GCP_PROJECT_ID` | Your GCP project ID |
| `PULUMI_ACCESS_TOKEN` | From https://app.pulumi.com/account/tokens |

Then delete the local key file:
```bash
rm /tmp/ci-sa-key.json
```

**Step 3: Create the workflow file**

Create `.github/workflows/deploy.yml`:
```yaml
name: Deploy to GCP

on:
  push:
    branches: [main]

env:
  GCP_REGION: us-central1
  REGISTRY: us-central1-docker.pkg.dev

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v4

      - name: Authenticate to GCP
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v2

      - name: Configure Docker for Artifact Registry
        run: gcloud auth configure-docker ${{ env.REGISTRY }} --quiet

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'

      - name: Build and push backend image
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/backend:${{ github.sha }}
          ./gradlew bootBuildImage --imageName=$IMAGE
          docker push $IMAGE

      - name: Build and push frontend image
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/frontend:${{ github.sha }}
          docker build -t $IMAGE ./frontend
          docker push $IMAGE

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'

      - name: Install Pulumi dependencies
        run: |
          cd infra
          python -m venv venv
          source venv/bin/activate
          pip install -r requirements.txt

      - name: Deploy with Pulumi
        run: |
          cd infra
          source venv/bin/activate
          pulumi stack select prod
          pulumi config set interview-hub-infra:backend_image \
            ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/backend:${{ github.sha }}
          pulumi config set interview-hub-infra:frontend_image \
            ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/frontend:${{ github.sha }}
          pulumi up --yes
        env:
          PULUMI_ACCESS_TOKEN: ${{ secrets.PULUMI_ACCESS_TOKEN }}
```

**Step 4: Commit the workflow**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat(ci): add GitHub Actions deploy workflow"
```

**Step 5: Push to main and watch the workflow run**

```bash
git push origin main
```

Go to GitHub → Actions and watch the deploy workflow. Expected: all steps green, Pulumi reports no changes (infra already up, only Cloud Run revisions updated).

**Step 6: Verify end-to-end**

```bash
curl https://yourdomain.com/actuator/health
# Expected: {"status":"UP"}

curl https://yourdomain.com/
# Expected: 200 with Angular HTML
```

---

## Notes

**nginx.conf — no changes needed.** The LB URL map intercepts API paths before requests reach the frontend Cloud Run service, so the existing `proxy_pass` blocks in `nginx.conf` are never triggered in production. Local Docker Compose development continues to work unchanged.

**Rollback:** Run `pulumi stack history` to see past updates, then `pulumi up` with a previous image SHA set in config. Cloud Run keeps the last 3 revisions and only shifts traffic after health checks pass.

**HTTP → HTTPS redirect:** Not included in this plan (scope). Can be added later with a second URL map + `TargetHttpProxy` + forwarding rule on port 80 that returns a 301.
