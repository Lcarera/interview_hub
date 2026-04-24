# GKE Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Interview Hub from Cloud Run (6 services) to GKE Standard (5 services, dropping Eureka) with Kubernetes-native service discovery, Workload Identity, and GCP Secret Manager integration.

**Architecture:** Replace Eureka with Kubernetes DNS for service discovery. API gateway routes to internal services via K8s Service DNS names. GKE Ingress with Cloud HTTP(S) Load Balancer handles external traffic with GCP-managed SSL. Secrets are synced from GCP Secret Manager to K8s Secrets via the managed Secret Manager addon (CSI driver). Workload Identity binds K8s service accounts to GCP service accounts for least-privilege access.

**Tech Stack:** GKE Standard (zonal, 2 nodes e2-standard-2), Pulumi (Python) for IaC + K8s resources, Spring Boot 4.0.2 / Spring Cloud 2025.1.1, Artifact Registry, GCP Secret Manager, Cloudflare DNS (A records)

---

## File Structure

### Files to Create
- `infra/gke.py` — GKE cluster + node pool definition
- `infra/networking.py` — Global static IP address
- `infra/kubernetes.py` — All K8s resources (namespace, SAs, SecretProviderClass, Deployments, Services, Ingress, ManagedCertificate, HPA)

### Files to Modify
- `services/api-gateway/build.gradle` — Remove Eureka dependency
- `services/api-gateway/src/main/resources/application.yml` — Replace `lb://core` with env var URL, remove Eureka config
- `services/api-gateway/src/test/resources/application-test.yml` — Remove Eureka config
- `services/core/build.gradle` — Remove Eureka dependency
- `services/core/src/main/resources/application.yml` — Add calendar-service URL property, remove Eureka config
- `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java` — Add `url` attribute to `@FeignClient`
- `services/core/src/test/resources/application-test.yml` — Remove Eureka config, add calendar-service URL
- `services/calendar-service/build.gradle` — Remove Eureka dependency
- `services/calendar-service/src/main/resources/application.yml` — Remove Eureka config
- `services/calendar-service/src/test/resources/application-test.yml` — Remove Eureka config
- `services/notification-service/build.gradle` — Remove Eureka dependency
- `services/notification-service/src/main/resources/application.yml` — Remove Eureka config
- `services/notification-service/src/test/resources/application-test.yml` — Remove Eureka config
- `settings.gradle` — Remove eureka-server module
- `compose.yaml` — Remove eureka-server service, replace Eureka URLs with direct service URLs
- `infra/__main__.py` — New exports for GKE cluster and static IP
- `infra/iam.py` — Rewrite for 4 GCP SAs + Workload Identity bindings + per-secret IAM
- `infra/Pulumi.prod.yaml` — New config keys for GKE
- `infra/requirements.txt` — Add pulumi-kubernetes dependency
- `infra/CLAUDE.md` — Update for GKE architecture
- `.github/workflows/deploy.yml` — Rewrite for GKE deployment
- `CLAUDE.md` — Update architecture sections
- `frontend/nginx.conf` — Route to api-gateway (unchanged service name, but confirm)

### Files to Delete
- `services/eureka-server/` — Entire directory (module removed)
- `infra/cloudrun.py` — Replaced by kubernetes.py + gke.py
- `cloudflare/worker.js` — Replaced by DNS A records
- `cloudflare/wrangler.toml` — No longer needed

---

## Task 1: Remove Eureka from api-gateway

**Files:**
- Modify: `services/api-gateway/build.gradle`
- Modify: `services/api-gateway/src/main/resources/application.yml`
- Modify: `services/api-gateway/src/test/resources/application-test.yml`

- [ ] **Step 1: Remove Eureka dependency from build.gradle**

In `services/api-gateway/build.gradle`, remove line 7:
```gradle
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

The remaining dependencies (`spring-cloud-starter-gateway-server-webflux`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`) are all still needed.

- [ ] **Step 2: Replace Eureka route URIs with env-var-based URLs in application.yml**

Replace the entire `services/api-gateway/src/main/resources/application.yml` with:
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: core-auth
          uri: ${CORE_URL:http://localhost:8080}
          predicates:
            - Path=/auth/**
        - id: core-actuator
          uri: ${CORE_URL:http://localhost:8080}
          predicates:
            - Path=/actuator/**
        - id: core-default
          uri: ${CORE_URL:http://localhost:8080}
          predicates:
            - Path=/**

app:
  jwt:
    signing-secret: ${JWT_SIGNING_SECRET}

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

Key change: `lb://core` (Eureka load-balanced) replaced with `${CORE_URL:http://localhost:8080}` (direct URL via env var). On K8s this will be `http://core:8080`, in compose it will be `http://app:8082`.

- [ ] **Step 3: Remove Eureka config from test profile**

Replace `services/api-gateway/src/test/resources/application-test.yml` with:
```yaml
app:
  jwt:
    signing-secret: test-secret-key-that-is-at-least-32-bytes-long

spring:
  cloud:
    gateway:
      routes: []  # disable routes in tests — we test security, not routing
```

The `eureka.client.enabled: false` line is no longer needed since Eureka is removed.

- [ ] **Step 4: Run api-gateway tests**

Run: `./gradlew :services:api-gateway:test`
Expected: All tests pass (SecurityConfigTest, ApiGatewayApplicationTest).

- [ ] **Step 5: Commit**

```bash
git add services/api-gateway/
git commit -m "refactor(api-gateway): replace Eureka with env-var-based routing

CORE_URL env var replaces lb://core Eureka discovery.
On K8s: http://core:8080 via DNS. In compose: http://app:8082."
```

---

## Task 2: Remove Eureka from core, parameterize calendar-service URL

**Files:**
- Modify: `services/core/build.gradle`
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java`
- Modify: `services/core/src/main/resources/application.yml`
- Modify: `services/core/src/test/resources/application-test.yml`

- [ ] **Step 1: Remove Eureka dependency from build.gradle**

In `services/core/build.gradle`, remove line 33:
```gradle
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

Keep `spring-cloud-starter-openfeign` (line 36) — it works without Eureka when a URL is specified.

- [ ] **Step 2: Add URL attribute to FeignClient**

Replace `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java` with:
```java
package com.gm2dev.interview_hub.client;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "calendar-service", url = "${app.calendar-service.url}")
public interface CalendarServiceClient {

    @PostMapping("/events")
    CalendarEventResponse createEvent(@RequestBody CalendarEventRequest request);

    @PutMapping("/events/{eventId}")
    void updateEvent(@PathVariable("eventId") String eventId, @RequestBody CalendarEventRequest request);

    @DeleteMapping("/events/{eventId}")
    void deleteEvent(@PathVariable("eventId") String eventId);

    @PostMapping("/events/{eventId}/attendees")
    void addAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);

    @PostMapping("/events/{eventId}/attendees/remove")
    void removeAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);
}
```

Key change: Added `url = "${app.calendar-service.url}"` — this bypasses service discovery entirely and calls the URL directly. On K8s this will be `http://calendar-service:8082`.

- [ ] **Step 3: Update application.yml — add calendar-service URL, remove Eureka**

In `services/core/src/main/resources/application.yml`, replace the `app:` section and remove `eureka:`:
```yaml
# Interview Hub backend configuration
spring:
  application:
    name: core

  datasource:
    url: ${DB_URL:jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:6543/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:3}
      minimum-idle: 1
      connection-timeout: 20000
      data-source-properties:
        prepareThreshold: 0

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false

  cloud:
    stream:
      bindings:
        email-out-0:
          destination: notification.emails
          content-type: application/json
  rabbitmq:
    addresses: ${RABBITMQ_URL:amqp://guest:guest@localhost:5672}

app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  calendar-service:
    url: ${CALENDAR_SERVICE_URL:http://localhost:8082}
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${APP_BASE_URL:http://localhost:8080}/auth/google/callback
  jwt:
    signing-secret: ${JWT_SIGNING_SECRET}
    expiration-seconds: 3600

logging:
  level:
    com.gm2dev.interview_hub: DEBUG
    org.springframework.security: DEBUG
```

Key changes: Added `app.calendar-service.url` property. Removed entire `eureka:` block.

- [ ] **Step 4: Update test profile — remove Eureka, add calendar-service URL**

Replace `services/core/src/test/resources/application-test.yml` with:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

app:
  base-url: http://localhost:8080
  frontend-url: http://localhost:4200
  calendar-service:
    url: http://localhost:8082
  google:
    client-id: test-client-id
    client-secret: test-client-secret
    redirect-uri: http://localhost:8080/auth/google/callback
  jwt:
    signing-secret: test-signing-secret-that-is-at-least-32-bytes-long
    expiration-seconds: 3600

logging:
  level:
    com.gm2dev.interview_hub: DEBUG
```

- [ ] **Step 5: Run core tests**

Run: `./gradlew :services:core:test`
Expected: All tests pass. FeignClient is mocked in service tests (`@MockitoBean CalendarServiceClient`), so the URL change doesn't affect test behavior.

- [ ] **Step 6: Commit**

```bash
git add services/core/
git commit -m "refactor(core): replace Eureka with direct URL for calendar-service

FeignClient now uses app.calendar-service.url property (CALENDAR_SERVICE_URL env var).
On K8s: http://calendar-service:8082 via DNS."
```

---

## Task 3: Remove Eureka from calendar-service and notification-service

**Files:**
- Modify: `services/calendar-service/build.gradle`
- Modify: `services/calendar-service/src/main/resources/application.yml`
- Modify: `services/calendar-service/src/test/resources/application-test.yml`
- Modify: `services/notification-service/build.gradle`
- Modify: `services/notification-service/src/main/resources/application.yml`
- Modify: `services/notification-service/src/test/resources/application-test.yml`

- [ ] **Step 1: Remove Eureka from calendar-service build.gradle**

In `services/calendar-service/build.gradle`, remove line 9:
```gradle
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

- [ ] **Step 2: Remove Eureka config from calendar-service application.yml**

Replace `services/calendar-service/src/main/resources/application.yml` with:
```yaml
server:
  port: 8082

spring:
  application:
    name: calendar-service

app:
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    calendar:
      id: ${GOOGLE_CALENDAR_ID:primary}
      refresh-token: ${GOOGLE_CALENDAR_REFRESH_TOKEN:}

management:
  endpoints:
    web:
      exposure:
        include: health

logging:
  level:
    com.gm2dev.calendar_service: DEBUG
```

- [ ] **Step 3: Remove Eureka from calendar-service test profile**

Replace `services/calendar-service/src/test/resources/application-test.yml` with:
```yaml
app:
  google:
    client-id: fake-client-id
    client-secret: fake-client-secret
    calendar:
      id: primary
      refresh-token: fake-refresh-token

logging:
  level:
    com.gm2dev.calendar_service: DEBUG
```

- [ ] **Step 4: Run calendar-service tests**

Run: `./gradlew :services:calendar-service:test`
Expected: All tests pass.

- [ ] **Step 5: Remove Eureka from notification-service build.gradle**

In `services/notification-service/build.gradle`, remove line 9:
```gradle
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

- [ ] **Step 6: Remove Eureka config from notification-service application.yml**

Replace `services/notification-service/src/main/resources/application.yml` with:
```yaml
server:
  port: ${PORT:8083}

spring:
  application:
    name: notification-service
  cloud:
    stream:
      bindings:
        processEmail-in-0:
          destination: notification.emails
          group: notification-service
          content-type: application/json
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
  rabbitmq:
    addresses: ${RABBITMQ_URL:amqp://guest:guest@localhost:5672}

app:
  frontend-url: ${FRONTEND_URL:http://localhost:4200}
  mail:
    from: ${MAIL_FROM:noreply@lcarera.dev}
  resend:
    api-key: ${RESEND_API_KEY:}

management:
  endpoints:
    web:
      exposure:
        include: health

logging:
  level:
    com.gm2dev.notification_service: DEBUG
```

- [ ] **Step 7: Remove Eureka from notification-service test profile**

Replace `services/notification-service/src/test/resources/application-test.yml` with:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration

app:
  frontend-url: http://localhost:4200
  mail:
    from: test@gm2dev.com
  resend:
    api-key: test-key
```

- [ ] **Step 8: Run notification-service tests**

Run: `./gradlew :services:notification-service:test`
Expected: All tests pass.

- [ ] **Step 9: Commit**

```bash
git add services/calendar-service/ services/notification-service/
git commit -m "refactor(calendar,notification): remove Eureka client dependency

Both services no longer register with Eureka. On K8s they are
discovered via Kubernetes DNS. On compose via Docker DNS."
```

---

## Task 4: Delete eureka-server module and update settings.gradle

**Files:**
- Delete: `services/eureka-server/` (entire directory)
- Modify: `settings.gradle`

- [ ] **Step 1: Remove eureka-server from settings.gradle**

In `settings.gradle`, remove the line:
```gradle
include 'services:eureka-server'
```

The file should become:
```gradle
rootProject.name = 'interview_hub'

include 'services:core'
include 'services:shared'
include 'services:notification-service'
include 'services:api-gateway'
include 'services:calendar-service'
```

- [ ] **Step 2: Delete eureka-server directory**

```bash
rm -rf services/eureka-server
```

- [ ] **Step 3: Verify Gradle still resolves**

Run: `./gradlew projects`
Expected: Lists 5 subprojects (core, shared, notification-service, api-gateway, calendar-service). No eureka-server.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: All tests pass across all remaining modules.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle
git rm -r services/eureka-server
git commit -m "chore: delete eureka-server module

Service discovery is now handled by Kubernetes DNS (GKE)
or Docker DNS (compose). Eureka is no longer needed."
```

---

## Task 5: Update compose.yaml for Eureka-free local dev

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Rewrite compose.yaml without eureka-server**

Replace `compose.yaml` with:
```yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  api-gateway:
    image: api-gateway:0.0.1-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
      JWT_SIGNING_SECRET: ${JWT_SIGNING_SECRET}
      CORE_URL: http://app:8082
    env_file:
      - path: .env
        required: false
    depends_on:
      app:
        condition: service_started
      calendar-service:
        condition: service_healthy

  notification-service:
    image: notification-service:0.0.1-SNAPSHOT
    environment:
      RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      MAIL_FROM: ${MAIL_FROM:-noreply@lcarera.dev}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost}
    depends_on:
      rabbitmq:
        condition: service_healthy

  calendar-service:
    image: calendar-service:0.0.1-SNAPSHOT
    ports:
      - "8082:8082"
    environment:
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
    env_file:
      - path: .env
        required: false
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  app:
    image: interview-hub:0.0.1-SNAPSHOT
    environment:
      SERVER_PORT: 8082
      DB_URL: ${DB_URL}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      JWT_SIGNING_SECRET: ${JWT_SIGNING_SECRET}
      APP_BASE_URL: ${APP_BASE_URL:-http://localhost:8080}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost}
      RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672
      CALENDAR_SERVICE_URL: http://calendar-service:8082
    env_file:
      - path: .env
        required: false
    depends_on:
      rabbitmq:
        condition: service_healthy
      calendar-service:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      args:
        NG_CONFIG: docker
    ports:
      - "80:80"
    depends_on:
      - api-gateway
```

Key changes:
- Removed `eureka-server` service entirely
- Removed all `EUREKA_URL` env vars
- Added `CORE_URL: http://app:8082` to api-gateway (core listens on 8082 in compose via SERVER_PORT override)
- Added `CALENDAR_SERVICE_URL: http://calendar-service:8082` to app
- Removed `eureka-server` from all `depends_on` blocks

- [ ] **Step 2: Commit**

```bash
git add compose.yaml
git commit -m "chore(compose): remove eureka-server, use direct service URLs

Services now communicate via Docker DNS names instead of Eureka
service discovery. CORE_URL and CALENDAR_SERVICE_URL replace lb:// URIs."
```

---

## Task 6: Pulumi — GKE cluster and node pool

**Files:**
- Create: `infra/gke.py`
- Modify: `infra/requirements.txt`

- [ ] **Step 1: Add pulumi-kubernetes to requirements.txt**

Replace `infra/requirements.txt` with:
```
pulumi>=3.0.0,<4.0.0
pulumi-gcp>=7.0.0,<8.0.0
pulumi-kubernetes>=4.0.0,<5.0.0
```

- [ ] **Step 2: Create gke.py**

Create `infra/gke.py`:
```python
"""GKE Standard cluster and node pool."""

import json
import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")
config = pulumi.Config("interview-hub-infra")
zone = config.get("zone") or "southamerica-east1-a"

cluster = gcp.container.Cluster(
    "interview-hub-cluster",
    name="interview-hub",
    location=zone,
    initial_node_count=1,
    remove_default_node_pool=True,
    workload_identity_config=gcp.container.ClusterWorkloadIdentityConfigArgs(
        workload_pool=f"{project}.svc.id.goog",
    ),
    secret_manager_config=gcp.container.ClusterSecretManagerConfigArgs(
        enabled=True,
    ),
    addons_config=gcp.container.ClusterAddonsConfigArgs(
        http_load_balancing=gcp.container.ClusterAddonsConfigHttpLoadBalancingArgs(
            disabled=False,
        ),
        horizontal_pod_autoscaling=gcp.container.ClusterAddonsConfigHorizontalPodAutoscalingArgs(
            disabled=False,
        ),
    ),
    deletion_protection=False,
    project=project,
)

node_pool = gcp.container.NodePool(
    "interview-hub-pool",
    name="interview-hub-pool",
    cluster=cluster.id,
    location=zone,
    node_count=2,
    node_config=gcp.container.NodePoolNodeConfigArgs(
        machine_type="e2-standard-2",
        disk_size_gb=50,
        oauth_scopes=["https://www.googleapis.com/auth/cloud-platform"],
        workload_metadata_config=gcp.container.NodePoolNodeConfigWorkloadMetadataConfigArgs(
            mode="GKE_METADATA",
        ),
    ),
)

# Build kubeconfig for the Pulumi Kubernetes provider
kubeconfig = pulumi.Output.all(
    cluster.name, cluster.endpoint, cluster.master_auth
).apply(
    lambda args: json.dumps(
        {
            "apiVersion": "v1",
            "clusters": [
                {
                    "cluster": {
                        "server": f"https://{args[1]}",
                        "certificate-authority-data": args[2].cluster_ca_certificate,
                    },
                    "name": "gke",
                }
            ],
            "contexts": [
                {"context": {"cluster": "gke", "user": "gke"}, "name": "gke"}
            ],
            "current-context": "gke",
            "users": [
                {
                    "name": "gke",
                    "user": {
                        "exec": {
                            "apiVersion": "client.authentication.k8s.io/v1beta1",
                            "command": "gke-gcloud-auth-plugin",
                            "installHint": "Install gke-gcloud-auth-plugin for kubectl auth",
                            "provideClusterInfo": True,
                        }
                    },
                }
            ],
        }
    )
)
```

- [ ] **Step 3: Commit**

```bash
git add infra/gke.py infra/requirements.txt
git commit -m "feat(infra): add GKE Standard cluster and node pool

Zonal cluster in southamerica-east1-a with 2x e2-standard-2 nodes.
Workload Identity and Secret Manager addon enabled.
Kubeconfig exported for Pulumi K8s provider."
```

---

## Task 7: Pulumi — IAM for GKE (GCP SAs + Workload Identity + per-secret access)

**Files:**
- Modify: `infra/iam.py`

- [ ] **Step 1: Rewrite iam.py for GKE**

Replace `infra/iam.py` with:
```python
"""GCP service accounts, Workload Identity bindings, and per-secret IAM."""

import pulumi
import pulumi_gcp as gcp
from secrets import secrets

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

NAMESPACE = "interview-hub"

# ── GCP Service Accounts (one per workload, least-privilege) ──

_sa_definitions = {
    "backend": {
        "account_id": "ih-backend",
        "display_name": "Interview Hub Backend",
        "secrets": [
            "DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
            "JWT_SIGNING_SECRET", "RABBITMQ_URL",
        ],
    },
    "notification": {
        "account_id": "ih-notification",
        "display_name": "Interview Hub Notification Service",
        "secrets": ["RESEND_API_KEY", "RABBITMQ_URL"],
    },
    "calendar": {
        "account_id": "ih-calendar",
        "display_name": "Interview Hub Calendar Service",
        "secrets": [
            "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
            "GOOGLE_CALENDAR_REFRESH_TOKEN",
        ],
    },
    "gateway": {
        "account_id": "ih-gateway",
        "display_name": "Interview Hub API Gateway",
        "secrets": ["JWT_SIGNING_SECRET"],
    },
}

gcp_service_accounts: dict[str, gcp.serviceaccount.Account] = {}
for name, defn in _sa_definitions.items():
    gcp_service_accounts[name] = gcp.serviceaccount.Account(
        f"{name}-sa",
        account_id=defn["account_id"],
        display_name=defn["display_name"],
        project=project,
    )

# ── Workload Identity bindings (K8s SA → GCP SA) ──

workload_identity_bindings: dict[str, gcp.serviceaccount.IAMMember] = {}
for name, sa in gcp_service_accounts.items():
    workload_identity_bindings[name] = gcp.serviceaccount.IAMMember(
        f"{name}-wi-binding",
        service_account_id=sa.name,
        role="roles/iam.workloadIdentityUser",
        member=pulumi.Output.concat(
            "serviceAccount:", project, ".svc.id.goog[",
            NAMESPACE, "/", name, "-sa]",
        ),
    )

# ── Per-secret IAM (each GCP SA can only access its own secrets) ──

for sa_name, defn in _sa_definitions.items():
    sa = gcp_service_accounts[sa_name]
    for secret_name in defn["secrets"]:
        gcp.secretmanager.SecretIamMember(
            f"{sa_name}-access-{secret_name.lower().replace('_', '-')}",
            secret_id=secrets[secret_name].secret_id,
            project=project,
            role="roles/secretmanager.secretAccessor",
            member=pulumi.Output.concat("serviceAccount:", sa.email),
        )
```

Key design: No project-level `secretmanager.secretAccessor` binding. Each GCP SA gets IAM access only to its specific secrets. This is true least-privilege.

- [ ] **Step 2: Commit**

```bash
git add infra/iam.py
git commit -m "feat(infra): rewrite IAM for GKE with Workload Identity

4 GCP SAs (backend, notification, calendar, gateway) with per-secret
IAM bindings. Workload Identity binds K8s SAs to GCP SAs."
```

---

## Task 8: Pulumi — Networking (static IP)

**Files:**
- Create: `infra/networking.py`

- [ ] **Step 1: Create networking.py**

Create `infra/networking.py`:
```python
"""Global static IP for the GKE Ingress load balancer."""

import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

static_ip = gcp.compute.GlobalAddress(
    "interview-hub-ip",
    name="interview-hub-ip",
    project=project,
)
```

The static IP is referenced by the Kubernetes Ingress annotation `kubernetes.io/ingress.global-static-ip-name`. The GCP-managed SSL certificate and ManagedCertificate CRD are created in `kubernetes.py` since they're K8s resources.

- [ ] **Step 2: Commit**

```bash
git add infra/networking.py
git commit -m "feat(infra): add global static IP for GKE Ingress"
```

---

## Task 9: Pulumi — Kubernetes resources

This is the largest task. It creates all K8s resources: namespace, service accounts, SecretProviderClasses, Deployments, Services, Ingress, ManagedCertificate, and HPA.

**Files:**
- Create: `infra/kubernetes.py`

- [ ] **Step 1: Create kubernetes.py — provider, namespace, service accounts**

Create `infra/kubernetes.py` with the first section:
```python
"""All Kubernetes resources for Interview Hub on GKE."""

import pulumi
import pulumi_kubernetes as k8s
from gke import cluster, kubeconfig
from iam import gcp_service_accounts
from networking import static_ip

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
domain = config.require("domain")
backend_domain = config.get("backend_domain") or "i-hub-be.lcarera.dev"

# Image URIs — set by CI at deploy time, fallback to placeholder
backend_image = config.get("backend_image") or "placeholder"
frontend_image = config.get("frontend_image") or "placeholder"
notification_image = config.get("notification_image") or "placeholder"
gateway_image = config.get("gateway_image") or "placeholder"
calendar_image = config.get("calendar_image") or "placeholder"

NAMESPACE = "interview-hub"

# ── Kubernetes Provider (authenticated via GKE kubeconfig) ──

k8s_provider = k8s.Provider(
    "gke-k8s",
    kubeconfig=kubeconfig,
    opts=pulumi.ResourceOptions(depends_on=[cluster]),
)
_k8s_opts = pulumi.ResourceOptions(provider=k8s_provider)

# ── Namespace ──

ns = k8s.core.v1.Namespace(
    "interview-hub-ns",
    metadata=k8s.meta.v1.ObjectMetaArgs(name=NAMESPACE),
    opts=_k8s_opts,
)
_ns_opts = pulumi.ResourceOptions(provider=k8s_provider, depends_on=[ns])

# ── Kubernetes Service Accounts (annotated for Workload Identity) ──

k8s_service_accounts: dict[str, k8s.core.v1.ServiceAccount] = {}
for name, gcp_sa in gcp_service_accounts.items():
    k8s_service_accounts[name] = k8s.core.v1.ServiceAccount(
        f"{name}-ksa",
        metadata=k8s.meta.v1.ObjectMetaArgs(
            name=f"{name}-sa",
            namespace=NAMESPACE,
            annotations={
                "iam.gke.io/gcp-service-account": gcp_sa.email,
            },
        ),
        opts=_ns_opts,
    )

# Frontend doesn't need GCP access — use default SA
frontend_ksa = k8s.core.v1.ServiceAccount(
    "frontend-ksa",
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name="frontend-sa",
        namespace=NAMESPACE,
    ),
    opts=_ns_opts,
)
```

- [ ] **Step 2: Add SecretProviderClass resources**

Append to `infra/kubernetes.py`:
```python
# ── SecretProviderClass (GCP Secret Manager → K8s Secrets via CSI driver) ──
#
# The GKE Secret Manager addon (enabled on the cluster) installs the
# Secrets Store CSI Driver + GCP provider automatically. Each
# SecretProviderClass defines which GCP SM secrets to mount and sync
# to a K8s Secret for env var injection.


def _build_spc(name: str, secret_names: list[str]) -> k8s.apiextensions.CustomResource:
    """Create a SecretProviderClass that syncs GCP SM secrets to a K8s Secret."""
    secret_id_map = {
        "DB_URL": "interview-hub-db-url",
        "DB_USERNAME": "interview-hub-db-username",
        "DB_PASSWORD": "interview-hub-db-password",
        "GOOGLE_CLIENT_ID": "interview-hub-google-client-id",
        "GOOGLE_CLIENT_SECRET": "interview-hub-google-client-secret",
        "JWT_SIGNING_SECRET": "interview-hub-jwt-signing-secret",
        "RESEND_API_KEY": "interview-hub-resend-api-key",
        "GOOGLE_CALENDAR_REFRESH_TOKEN": "interview-hub-google-calendar-refresh-token",
        "RABBITMQ_URL": "interview-hub-rabbitmq-url",
    }

    secrets_yaml_lines = []
    secret_objects_data = []
    for sn in secret_names:
        sid = secret_id_map[sn]
        secrets_yaml_lines.append(
            f'- resourceName: "projects/{project}/secrets/{sid}/versions/latest"\n'
            f'  path: "{sn}"'
        )
        secret_objects_data.append({"objectName": sn, "key": sn})

    return k8s.apiextensions.CustomResource(
        f"{name}-spc",
        api_version="secrets-store.csi.x-k8s.io/v1",
        kind="SecretProviderClass",
        metadata=k8s.meta.v1.ObjectMetaArgs(
            name=f"{name}-secrets",
            namespace=NAMESPACE,
        ),
        other_fields={
            "spec": {
                "provider": "gcp",
                "parameters": {
                    "secrets": "\n".join(secrets_yaml_lines),
                },
                "secretObjects": [
                    {
                        "secretName": f"{name}-secrets",
                        "type": "Opaque",
                        "data": secret_objects_data,
                    }
                ],
            },
        },
        opts=_ns_opts,
    )


backend_spc = _build_spc("backend", [
    "DB_URL", "DB_USERNAME", "DB_PASSWORD",
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
    "JWT_SIGNING_SECRET", "RABBITMQ_URL",
])

notification_spc = _build_spc("notification", [
    "RESEND_API_KEY", "RABBITMQ_URL",
])

calendar_spc = _build_spc("calendar", [
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
    "GOOGLE_CALENDAR_REFRESH_TOKEN",
])

gateway_spc = _build_spc("gateway", [
    "JWT_SIGNING_SECRET",
])
```

- [ ] **Step 3: Add Deployment and Service helper**

Append to `infra/kubernetes.py`:
```python
# ── Deployment + Service helpers ──


def _deployment(
    name: str,
    image: str,
    container_port: int,
    sa_name: str,
    spc_name: str,
    env: list[dict] | None = None,
    replicas: int = 1,
    memory_request: str = "512Mi",
    memory_limit: str = "512Mi",
    cpu_request: str = "250m",
    cpu_limit: str = "500m",
) -> k8s.apps.v1.Deployment:
    env_list = [k8s.core.v1.EnvVarArgs(**e) for e in (env or [])]
    return k8s.apps.v1.Deployment(
        f"{name}-deployment",
        metadata=k8s.meta.v1.ObjectMetaArgs(
            name=name, namespace=NAMESPACE,
        ),
        spec=k8s.apps.v1.DeploymentSpecArgs(
            replicas=replicas,
            selector=k8s.meta.v1.LabelSelectorArgs(
                match_labels={"app": name},
            ),
            template=k8s.core.v1.PodTemplateSpecArgs(
                metadata=k8s.meta.v1.ObjectMetaArgs(
                    labels={"app": name},
                ),
                spec=k8s.core.v1.PodSpecArgs(
                    service_account_name=sa_name,
                    containers=[
                        k8s.core.v1.ContainerArgs(
                            name=name,
                            image=image,
                            ports=[k8s.core.v1.ContainerPortArgs(
                                container_port=container_port,
                            )],
                            env_from=[
                                k8s.core.v1.EnvFromSourceArgs(
                                    secret_ref=k8s.core.v1.SecretEnvSourceArgs(
                                        name=f"{spc_name}-secrets",
                                    ),
                                ),
                            ],
                            env=env_list,
                            volume_mounts=[
                                k8s.core.v1.VolumeMountArgs(
                                    name="secrets",
                                    mount_path="/mnt/secrets",
                                    read_only=True,
                                ),
                            ],
                            readiness_probe=k8s.core.v1.ProbeArgs(
                                http_get=k8s.core.v1.HTTPGetActionArgs(
                                    path="/actuator/health",
                                    port=container_port,
                                ),
                                initial_delay_seconds=30,
                                period_seconds=10,
                            ),
                            liveness_probe=k8s.core.v1.ProbeArgs(
                                http_get=k8s.core.v1.HTTPGetActionArgs(
                                    path="/actuator/health",
                                    port=container_port,
                                ),
                                initial_delay_seconds=60,
                                period_seconds=15,
                            ),
                            resources=k8s.core.v1.ResourceRequirementsArgs(
                                requests={"memory": memory_request, "cpu": cpu_request},
                                limits={"memory": memory_limit, "cpu": cpu_limit},
                            ),
                        ),
                    ],
                    volumes=[
                        k8s.core.v1.VolumeArgs(
                            name="secrets",
                            csi=k8s.core.v1.CSIVolumeSourceArgs(
                                driver="secrets-store.csi.k8s.io",
                                read_only=True,
                                volume_attributes={
                                    "secretProviderClass": f"{spc_name}-secrets",
                                },
                            ),
                        ),
                    ],
                ),
            ),
        ),
        opts=_ns_opts,
    )


def _service(name: str, port: int, target_port: int) -> k8s.core.v1.Service:
    return k8s.core.v1.Service(
        f"{name}-service",
        metadata=k8s.meta.v1.ObjectMetaArgs(
            name=name, namespace=NAMESPACE,
        ),
        spec=k8s.core.v1.ServiceSpecArgs(
            selector={"app": name},
            ports=[k8s.core.v1.ServicePortArgs(
                port=port, target_port=target_port, protocol="TCP",
            )],
            type="ClusterIP",
        ),
        opts=_ns_opts,
    )
```

- [ ] **Step 4: Create all Deployments and Services**

Append to `infra/kubernetes.py`:
```python
# ── Workloads ──

# API Gateway — public entry point, routes to internal services
gateway_deployment = _deployment(
    name="api-gateway",
    image=gateway_image,
    container_port=8080,
    sa_name="gateway-sa",
    spc_name="gateway",
    env=[{"name": "CORE_URL", "value": "http://core:8080"}],
)
gateway_service = _service("api-gateway", port=8080, target_port=8080)

# Core backend — business logic, DB, auth
core_deployment = _deployment(
    name="core",
    image=backend_image,
    container_port=8080,
    sa_name="backend-sa",
    spc_name="backend",
    env=[
        {"name": "APP_BASE_URL", "value": f"https://{backend_domain}"},
        {"name": "FRONTEND_URL", "value": f"https://{domain}"},
        {"name": "CALENDAR_SERVICE_URL", "value": "http://calendar-service:8082"},
        {"name": "GOOGLE_CALENDAR_ID", "value": "0cae724ce3870858a6213c7f351107891bd3c1265b336d3bfef5693c3a3cdc9d@group.calendar.google.com"},
    ],
    memory_request="768Mi",
    memory_limit="1Gi",
    cpu_request="500m",
    cpu_limit="1000m",
)
core_service = _service("core", port=8080, target_port=8080)

# Calendar service — Google Calendar integration
calendar_deployment = _deployment(
    name="calendar-service",
    image=calendar_image,
    container_port=8082,
    sa_name="calendar-sa",
    spc_name="calendar",
    env=[
        {"name": "GOOGLE_CALENDAR_ID", "value": "0cae724ce3870858a6213c7f351107891bd3c1265b336d3bfef5693c3a3cdc9d@group.calendar.google.com"},
    ],
)
calendar_service_k8s = _service("calendar-service", port=8082, target_port=8082)

# Notification service — RabbitMQ consumer, sends emails via Resend
notification_deployment = _deployment(
    name="notification-service",
    image=notification_image,
    container_port=8083,
    sa_name="notification-sa",
    spc_name="notification",
    env=[
        {"name": "FRONTEND_URL", "value": f"https://{domain}"},
        {"name": "MAIL_FROM", "value": "noreply@lcarera.dev"},
    ],
)
# No Service for notification — it only consumes from RabbitMQ (outbound only)

# Frontend — nginx serving Angular SPA
frontend_deployment = k8s.apps.v1.Deployment(
    "frontend-deployment",
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name="frontend", namespace=NAMESPACE,
    ),
    spec=k8s.apps.v1.DeploymentSpecArgs(
        replicas=1,
        selector=k8s.meta.v1.LabelSelectorArgs(
            match_labels={"app": "frontend"},
        ),
        template=k8s.core.v1.PodTemplateSpecArgs(
            metadata=k8s.meta.v1.ObjectMetaArgs(
                labels={"app": "frontend"},
            ),
            spec=k8s.core.v1.PodSpecArgs(
                service_account_name="frontend-sa",
                containers=[
                    k8s.core.v1.ContainerArgs(
                        name="frontend",
                        image=frontend_image,
                        ports=[k8s.core.v1.ContainerPortArgs(container_port=80)],
                        readiness_probe=k8s.core.v1.ProbeArgs(
                            http_get=k8s.core.v1.HTTPGetActionArgs(
                                path="/", port=80,
                            ),
                            initial_delay_seconds=5,
                            period_seconds=10,
                        ),
                        resources=k8s.core.v1.ResourceRequirementsArgs(
                            requests={"memory": "64Mi", "cpu": "50m"},
                            limits={"memory": "128Mi", "cpu": "100m"},
                        ),
                    ),
                ],
            ),
        ),
    ),
    opts=_ns_opts,
)
frontend_service = _service("frontend", port=80, target_port=80)
```

- [ ] **Step 5: Add Ingress, ManagedCertificate, and HPA**

Append to `infra/kubernetes.py`:
```python
# ── ManagedCertificate (GCP-managed SSL, auto-renewing) ──

managed_cert = k8s.apiextensions.CustomResource(
    "interview-hub-cert",
    api_version="networking.gke.io/v1",
    kind="ManagedCertificate",
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name="interview-hub-cert",
        namespace=NAMESPACE,
    ),
    other_fields={
        "spec": {
            "domains": [domain, backend_domain],
        },
    },
    opts=_ns_opts,
)

# ── Ingress (Cloud HTTP(S) Load Balancer) ──

ingress = k8s.networking.v1.Ingress(
    "interview-hub-ingress",
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name="interview-hub",
        namespace=NAMESPACE,
        annotations={
            "kubernetes.io/ingress.global-static-ip-name": "interview-hub-ip",
            "networking.gke.io/managed-certificates": "interview-hub-cert",
            "kubernetes.io/ingress.class": "gce",
        },
    ),
    spec=k8s.networking.v1.IngressSpecArgs(
        rules=[
            k8s.networking.v1.IngressRuleArgs(
                host=domain,
                http=k8s.networking.v1.HTTPIngressRuleValueArgs(
                    paths=[
                        k8s.networking.v1.HTTPIngressPathArgs(
                            path="/*",
                            path_type="ImplementationSpecific",
                            backend=k8s.networking.v1.IngressBackendArgs(
                                service=k8s.networking.v1.IngressServiceBackendArgs(
                                    name="frontend",
                                    port=k8s.networking.v1.ServiceBackendPortArgs(
                                        number=80,
                                    ),
                                ),
                            ),
                        ),
                    ],
                ),
            ),
            k8s.networking.v1.IngressRuleArgs(
                host=backend_domain,
                http=k8s.networking.v1.HTTPIngressRuleValueArgs(
                    paths=[
                        k8s.networking.v1.HTTPIngressPathArgs(
                            path="/*",
                            path_type="ImplementationSpecific",
                            backend=k8s.networking.v1.IngressBackendArgs(
                                service=k8s.networking.v1.IngressServiceBackendArgs(
                                    name="api-gateway",
                                    port=k8s.networking.v1.ServiceBackendPortArgs(
                                        number=8080,
                                    ),
                                ),
                            ),
                        ),
                    ],
                ),
            ),
        ],
    ),
    opts=_ns_opts,
)

# ── HPA for core backend (CPU-based, 1-5 replicas) ──

core_hpa = k8s.autoscaling.v2.HorizontalPodAutoscaler(
    "core-hpa",
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name="core-hpa",
        namespace=NAMESPACE,
    ),
    spec=k8s.autoscaling.v2.HorizontalPodAutoscalerSpecArgs(
        scale_target_ref=k8s.autoscaling.v2.CrossVersionObjectReferenceArgs(
            api_version="apps/v1",
            kind="Deployment",
            name="core",
        ),
        min_replicas=1,
        max_replicas=5,
        metrics=[
            k8s.autoscaling.v2.MetricSpecArgs(
                type="Resource",
                resource=k8s.autoscaling.v2.ResourceMetricSourceArgs(
                    name="cpu",
                    target=k8s.autoscaling.v2.MetricTargetArgs(
                        type="Utilization",
                        average_utilization=70,
                    ),
                ),
            ),
        ],
    ),
    opts=_ns_opts,
)
```

- [ ] **Step 6: Commit**

```bash
git add infra/kubernetes.py
git commit -m "feat(infra): add all Kubernetes resources for GKE

Namespace, K8s SAs (Workload Identity), SecretProviderClasses (CSI),
Deployments (5), Services (4), ManagedCertificate, Ingress (host-based
routing), HPA for core (1-5 replicas, 70% CPU)."
```

---

## Task 10: Pulumi — Update entrypoint, config, and delete Cloud Run

**Files:**
- Modify: `infra/__main__.py`
- Modify: `infra/Pulumi.prod.yaml`
- Delete: `infra/cloudrun.py`

- [ ] **Step 1: Rewrite __main__.py**

Replace `infra/__main__.py` with:
```python
import pulumi
from registry import registry_url
from gke import cluster
from networking import static_ip
import iam  # noqa: F401 — imported for side effects (SA + WI bindings)
import secrets  # noqa: F401 — imported for side effects (Secret Manager)
import kubernetes  # noqa: F401 — imported for side effects (K8s resources)

pulumi.export("registry_url", registry_url)
pulumi.export("cluster_name", cluster.name)
pulumi.export("cluster_endpoint", cluster.endpoint)
pulumi.export("static_ip", static_ip.address)
```

- [ ] **Step 2: Update Pulumi.prod.yaml**

Replace `infra/Pulumi.prod.yaml` with:
```yaml
config:
  gcp:project: interview-hub-prod
  gcp:region: southamerica-east1
  interview-hub-infra:zone: southamerica-east1-a
  interview-hub-infra:domain: interview-hub.lcarera.dev
  interview-hub-infra:backend_domain: i-hub-be.lcarera.dev
```

Image configs (`backend_image`, `frontend_image`, etc.) are set dynamically by CI. They default to `"placeholder"` in the code.

- [ ] **Step 3: Delete cloudrun.py**

```bash
rm infra/cloudrun.py
```

- [ ] **Step 4: Install Pulumi dependencies and verify**

```bash
cd infra && python -m venv venv && source venv/bin/activate && pip install -r requirements.txt
```

Then verify the stack parses correctly:
```bash
pulumi preview --stack prod 2>&1 | head -50
```

Expected: Preview shows new GKE resources to create and old Cloud Run resources to delete. May error on actual API calls if not authenticated, but the Python should parse without import errors.

- [ ] **Step 5: Commit**

```bash
git add infra/__main__.py infra/Pulumi.prod.yaml
git rm infra/cloudrun.py
git commit -m "feat(infra): replace Cloud Run with GKE in Pulumi entrypoint

Exports cluster_name, cluster_endpoint, static_ip instead of
per-service Cloud Run URIs. Deletes cloudrun.py."
```

---

## Task 11: Rewrite CI/CD deploy workflow for GKE

**Files:**
- Modify: `.github/workflows/deploy.yml`

- [ ] **Step 1: Rewrite deploy.yml**

Replace `.github/workflows/deploy.yml` with:
```yaml
name: Deploy to GCP

on:
  push:
    branches: [prod]
  workflow_dispatch:
    inputs:
      force_full_deploy:
        description: 'Deploy all services (skip change detection)'
        type: boolean
        default: true

env:
  GCP_REGION: southamerica-east1
  REGISTRY: southamerica-east1-docker.pkg.dev

jobs:
  changes:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    outputs:
      backend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.backend }}
      frontend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.frontend }}
      infra: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.infra }}
      migrations: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.migrations }}
      notification: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.notification }}
      gateway: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.gateway }}
      calendar: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.calendar }}
      base_sha: ${{ steps.base.outputs.sha }}
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Determine comparison base
        id: base
        env:
          EVENT_BEFORE: ${{ github.event.before }}
        run: |
          if [ -z "$EVENT_BEFORE" ] || [ "$EVENT_BEFORE" = "0000000000000000000000000000000000000000" ]; then
            echo "sha=$(git rev-parse HEAD~1)" >> "$GITHUB_OUTPUT"
          else
            echo "sha=$EVENT_BEFORE" >> "$GITHUB_OUTPUT"
          fi
      - uses: dorny/paths-filter@v3
        id: filter
        with:
          base: ${{ steps.base.outputs.sha }}
          filters: |
            backend:
              - 'services/core/**'
              - 'services/shared/**'
              - 'build.gradle'
              - 'settings.gradle'
              - 'gradle/**'
              - 'gradlew'
              - 'gradlew.bat'
            frontend:
              - 'frontend/**'
            infra:
              - 'infra/**'
              - '.github/workflows/deploy.yml'
            migrations:
              - 'supabase/migrations/**'
            notification:
              - 'services/notification-service/**'
              - 'services/shared/**'
              - 'build.gradle'
              - 'settings.gradle'
              - 'gradle/**'
              - 'gradlew'
              - 'gradlew.bat'
            gateway:
              - 'services/api-gateway/**'
              - 'build.gradle'
              - 'settings.gradle'
              - 'gradle/**'
              - 'gradlew'
              - 'gradlew.bat'
            calendar:
              - 'services/calendar-service/**'
              - 'services/shared/**'
              - 'build.gradle'
              - 'settings.gradle'
              - 'gradle/**'
              - 'gradlew'
              - 'gradlew.bat'

  deploy:
    needs: changes
    if: needs.changes.outputs.backend == 'true' || needs.changes.outputs.frontend == 'true' || needs.changes.outputs.infra == 'true' || needs.changes.outputs.migrations == 'true' || needs.changes.outputs.notification == 'true' || needs.changes.outputs.gateway == 'true' || needs.changes.outputs.calendar == 'true'
    runs-on: ubuntu-latest
    permissions:
      contents: read
    env:
      PULUMI_ACCESS_TOKEN: ${{ secrets.PULUMI_ACCESS_TOKEN }}

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Authenticate to GCP
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v2

      - name: Install gke-gcloud-auth-plugin
        run: gcloud components install gke-gcloud-auth-plugin --quiet

      - name: Configure Docker for Artifact Registry
        run: gcloud auth configure-docker ${{ env.REGISTRY }} --quiet

      - name: Install PostgreSQL client
        if: needs.changes.outputs.migrations == 'true'
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends postgresql-client

      - name: Run Supabase migrations
        if: needs.changes.outputs.migrations == 'true'
        env:
          SUPABASE_DB_URL: ${{ secrets.SUPABASE_DB_URL }}
          BASE_SHA: ${{ needs.changes.outputs.base_sha }}
          CURRENT_SHA: ${{ github.sha }}
        run: |
          if [ -z "$SUPABASE_DB_URL" ]; then
            echo "::error::SUPABASE_DB_URL secret is not set"
            exit 1
          fi
          if [ -z "$BASE_SHA" ]; then
            echo "No base SHA available; skipping migration detection."
            exit 0
          fi
          NEW_FILES=$(git diff --name-only --diff-filter=A "$BASE_SHA" "$CURRENT_SHA" -- 'supabase/migrations/*.sql' | sort)
          if [ -z "$NEW_FILES" ]; then
            echo "No new migration files detected, skipping."
            exit 0
          fi
          echo "New migration files to apply:"
          echo "$NEW_FILES"
          for file in $NEW_FILES; do
            echo "Applying migration: $file"
            psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f "$file"
          done
          echo "All migrations applied successfully."

      - name: Set up Java 25
        if: needs.changes.outputs.backend == 'true' || needs.changes.outputs.notification == 'true' || needs.changes.outputs.gateway == 'true' || needs.changes.outputs.calendar == 'true'
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'

      - name: Build and push backend image
        if: needs.changes.outputs.backend == 'true'
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/backend:${{ github.sha }}
          ./gradlew :services:core:bootBuildImage --imageName=$IMAGE
          docker push $IMAGE

      - name: Build and push notification image
        if: needs.changes.outputs.notification == 'true'
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/notification-service:${{ github.sha }}
          ./gradlew :services:notification-service:bootBuildImage --imageName=$IMAGE
          docker push $IMAGE

      - name: Build and push gateway image
        if: needs.changes.outputs.gateway == 'true'
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/api-gateway:${{ github.sha }}
          ./gradlew :services:api-gateway:bootBuildImage --imageName=$IMAGE
          docker push $IMAGE

      - name: Build and push calendar image
        if: needs.changes.outputs.calendar == 'true'
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/calendar:${{ github.sha }}
          ./gradlew :services:calendar-service:bootBuildImage --imageName=$IMAGE
          docker push $IMAGE

      - name: Build and push frontend image
        if: needs.changes.outputs.frontend == 'true'
        run: |
          IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/frontend:${{ github.sha }}
          docker build --build-arg NGINX_CONF=nginx.prod.conf -t $IMAGE ./frontend
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

          # Set image configs for changed services; retain current for unchanged
          CLUSTER="interview-hub"
          ZONE="southamerica-east1-a"

          get_current_image() {
            local DEPLOYMENT=$1
            gcloud container clusters get-credentials "$CLUSTER" --zone="$ZONE" --quiet 2>/dev/null
            kubectl get deployment "$DEPLOYMENT" -n interview-hub \
              -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo ""
          }

          if [ "${{ needs.changes.outputs.backend }}" == "true" ]; then
            pulumi config set interview-hub-infra:backend_image \
              ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/backend:${{ github.sha }}
          else
            CURRENT=$(get_current_image "core")
            [ -n "$CURRENT" ] && pulumi config set interview-hub-infra:backend_image "$CURRENT"
          fi

          if [ "${{ needs.changes.outputs.frontend }}" == "true" ]; then
            pulumi config set interview-hub-infra:frontend_image \
              ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/frontend:${{ github.sha }}
          else
            CURRENT=$(get_current_image "frontend")
            [ -n "$CURRENT" ] && pulumi config set interview-hub-infra:frontend_image "$CURRENT"
          fi

          if [ "${{ needs.changes.outputs.notification }}" == "true" ]; then
            pulumi config set interview-hub-infra:notification_image \
              ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/notification-service:${{ github.sha }}
          else
            CURRENT=$(get_current_image "notification-service")
            [ -n "$CURRENT" ] && pulumi config set interview-hub-infra:notification_image "$CURRENT"
          fi

          if [ "${{ needs.changes.outputs.gateway }}" == "true" ]; then
            pulumi config set interview-hub-infra:gateway_image \
              ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/api-gateway:${{ github.sha }}
          else
            CURRENT=$(get_current_image "api-gateway")
            [ -n "$CURRENT" ] && pulumi config set interview-hub-infra:gateway_image "$CURRENT"
          fi

          if [ "${{ needs.changes.outputs.calendar }}" == "true" ]; then
            pulumi config set interview-hub-infra:calendar_image \
              ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/calendar:${{ github.sha }}
          else
            CURRENT=$(get_current_image "calendar-service")
            [ -n "$CURRENT" ] && pulumi config set interview-hub-infra:calendar_image "$CURRENT"
          fi

          pulumi up --yes
```

Key changes from the Cloud Run version:
- Removed `eureka` from change detection and build steps
- `get_current_image` now reads from K8s Deployments via `kubectl` instead of Cloud Run services via `gcloud run`
- Added `gke-gcloud-auth-plugin` installation step
- The Pulumi deploy creates/updates K8s resources instead of Cloud Run services

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat(ci): rewrite deploy workflow for GKE

Removes eureka build step. Reads current images from K8s Deployments
instead of Cloud Run services. Installs gke-gcloud-auth-plugin."
```

---

## Task 12: Delete Cloudflare Worker

**Files:**
- Delete: `cloudflare/worker.js`
- Delete: `cloudflare/wrangler.toml`

- [ ] **Step 1: Delete Cloudflare Worker files**

```bash
rm -rf cloudflare
```

The Cloudflare Worker was a workaround for Cloud Run's lack of custom domain support. GKE Ingress handles host-based routing natively via the Cloud HTTP(S) Load Balancer. Cloudflare is now just DNS (A records pointing to the static IP).

- [ ] **Step 2: Manual DNS update (not automated)**

After deploying to GKE, get the static IP:
```bash
cd infra && source venv/bin/activate && pulumi stack output static_ip
```

Then in Cloudflare Dashboard:
1. Delete the Workers routes for `interview-hub.lcarera.dev` and `i-hub-be.lcarera.dev`
2. Create/update DNS A records:
   - `interview-hub.lcarera.dev` → `<static_ip>` (Proxied or DNS-only)
   - `i-hub-be.lcarera.dev` → `<static_ip>` (Proxied or DNS-only)

**Important:** If using Cloudflare Proxy (orange cloud), set to DNS-only (grey cloud) initially to let GCP-managed SSL validate. After the ManagedCertificate shows `Active` status, you can re-enable Cloudflare Proxy if desired.

- [ ] **Step 3: Commit**

```bash
git rm -r cloudflare
git commit -m "chore: delete Cloudflare Worker — replaced by GKE Ingress

GKE Ingress handles host-based routing natively via Cloud HTTP(S) LB.
Cloudflare now serves as DNS only (A records to static IP)."
```

---

## Task 13: Update documentation

**Files:**
- Modify: `CLAUDE.md` — Architecture sections
- Modify: `infra/CLAUDE.md` — Full rewrite for GKE

- [ ] **Step 1: Update infra/CLAUDE.md**

Replace `infra/CLAUDE.md` with:
```markdown
# Infrastructure (Pulumi + GKE)

Pulumi Python project managing GKE infrastructure for Interview Hub. Stack: `prod`.

## Commands

\```bash
cd infra
source venv/bin/activate   # Python virtualenv
pulumi up                  # preview + deploy
pulumi preview             # dry-run only
pulumi stack output        # show exported values (registry_url, cluster_name, static_ip)
\```

## Module Structure

- `__main__.py` — Entrypoint. Imports all modules for side effects and exports stack outputs.
- `registry.py` — Artifact Registry repo (`interview-hub`) for Docker images.
- `gke.py` — GKE Standard cluster (zonal, southamerica-east1-a) + node pool (2x e2-standard-2). Workload Identity and Secret Manager addon enabled. Exports `cluster`, `kubeconfig`.
- `iam.py` — 4 GCP SAs (backend, notification, calendar, gateway) with Workload Identity bindings and per-secret IAM access.
- `secrets.py` — GCP Secret Manager secrets (9 secrets). Values set manually via `gcloud`.
- `networking.py` — Global static IP for the GKE Ingress load balancer.
- `kubernetes.py` — All K8s resources: namespace, service accounts, SecretProviderClasses (CSI driver), Deployments (5), Services (4), ManagedCertificate (SSL), Ingress (host-based routing), HPA (core backend, 1-5 replicas).

## Config Values (`Pulumi.prod.yaml`)

- `gcp:project` / `gcp:region` — GCP project and region
- `interview-hub-infra:zone` — GKE cluster zone (southamerica-east1-a)
- `interview-hub-infra:domain` — Frontend custom domain
- `interview-hub-infra:backend_domain` — Backend/gateway custom domain
- `interview-hub-infra:backend_image` / `frontend_image` / `notification_image` / `gateway_image` / `calendar_image` — Set by CI at deploy time; fallback to `"placeholder"`

## Secret Flow

```
GCP Secret Manager → SecretProviderClass (CSI driver) → K8s Secret → Pod env vars
```

Each GCP SA has IAM access only to its own secrets (per-secret `secretmanager.secretAccessor`). Pods authenticate via Workload Identity (K8s SA → GCP SA).

## Workload Identity

| K8s SA | GCP SA | Secrets |
|--------|--------|---------|
| backend-sa | ih-backend | DB_URL, DB_USERNAME, DB_PASSWORD, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, JWT_SIGNING_SECRET, RABBITMQ_URL |
| notification-sa | ih-notification | RESEND_API_KEY, RABBITMQ_URL |
| calendar-sa | ih-calendar | GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_CALENDAR_REFRESH_TOKEN |
| gateway-sa | ih-gateway | JWT_SIGNING_SECRET |
| frontend-sa | (none) | (none) |

## Key Design Decisions

- **No Eureka** — Kubernetes DNS handles service discovery. Gateway routes to `http://core:8080`, core calls `http://calendar-service:8082`.
- **Secrets via CSI driver** — GKE Secret Manager addon mounts secrets from GCP SM into pods. No secret values in Pulumi state.
- **Least-privilege** — Per-secret IAM bindings, not project-level `secretmanager.secretAccessor`.
- **Public cluster** — Nodes have public IPs. Simplifies `kubectl` access for learning.
- **No local state** — Pulumi state is managed remotely (Pulumi Cloud).
```

- [ ] **Step 2: Update relevant CLAUDE.md sections**

In the root `CLAUDE.md`, update these sections:
- **Docker & Deployment** — Replace Cloud Run descriptions with GKE
- **Microservices Deploy Strategy** — Remove the "deploy all 4 plans together" section (no longer relevant)
- **Multi-Module Gradle** — Remove eureka-server references
- **Environment Variables** — Remove `EUREKA_URL`, add `CORE_URL`, `CALENDAR_SERVICE_URL`

These are documentation-only changes. The specific edits depend on the current CLAUDE.md content at execution time.

- [ ] **Step 3: Commit**

```bash
git add infra/CLAUDE.md CLAUDE.md
git commit -m "docs: update documentation for GKE migration

Replace Cloud Run references with GKE architecture. Remove Eureka
references. Document Workload Identity and SecretProviderClass flow."
```

---

## Future Improvements (not in this plan)

These were discussed during the architecture grilling session and deferred:

1. **Migrate Supabase → Cloud SQL for PostgreSQL** — enables private networking (pods talk to Cloud SQL over private IP via Cloud SQL Auth Proxy sidecar)
2. **Migrate CloudAMQP → Cloud Pub/Sub** — fully managed, no external dependency, rewrite Spring Cloud Stream bindings
3. **Replace Cloudflare DNS → Cloud DNS** — native GCP, simplifies certificate validation
4. **Add observability** — Managed Prometheus + Grafana for custom metrics, Cloud Trace for distributed tracing
5. **Private cluster** — move to private nodes + Cloud NAT for outbound internet, authorized networks for kubectl
6. **Network Policies** — restrict pod-to-pod traffic (e.g., notification-service should only reach RabbitMQ and Resend, not core or calendar-service)
