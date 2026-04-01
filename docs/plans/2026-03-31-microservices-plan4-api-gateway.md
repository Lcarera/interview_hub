# Microservices Migration — Plan 4: Spring Cloud Gateway

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `api-gateway` as the single entry point for all API traffic. The gateway validates JWTs, enforces authorization rules, and load-balances to downstream services via Eureka. `core`'s port moves to internal-only 8082 so nothing is reachable without going through the gateway. `nginx` is updated to proxy API calls to `api-gateway` instead of directly to `core`.

**Architecture:** Spring Cloud Gateway (WebFlux/reactive) sits at port 8080 (the same port `core` used to occupy externally). It registers with Eureka and routes all traffic to `lb://core` for now. JWT validation is duplicated at the gateway (defense in depth — `core` continues to validate too). `nginx` remains the TLS/static-file layer; it now proxies to `http://api-gateway:8080` instead of `http://app:8080`.

**Tech Stack:** Spring Boot 4.0.2, Spring Cloud Gateway (WebFlux), Spring Security WebFlux (`ServerHttpSecurity` / `SecurityWebFilterChain`), Netflix Eureka client, Nimbus JOSE+JWT (HMAC-SHA256), Java 25.

> **WebFlux note:** Spring Cloud Gateway is reactive. All security configuration uses `@EnableWebFluxSecurity`, `ServerHttpSecurity`, `SecurityWebFilterChain`, and `ReactiveJwtDecoder` — NOT the MVC equivalents (`HttpSecurity`, `SecurityFilterChain`, `JwtDecoder`).

---

## File Map

**Create:**
- `services/api-gateway/build.gradle`
- `services/api-gateway/src/main/java/com/gm2dev/api_gateway/ApiGatewayApplication.java`
- `services/api-gateway/src/main/java/com/gm2dev/api_gateway/config/SecurityConfig.java`
- `services/api-gateway/src/main/java/com/gm2dev/api_gateway/config/JwtProperties.java`
- `services/api-gateway/src/main/resources/application.yml`
- `services/api-gateway/src/test/java/com/gm2dev/api_gateway/ApiGatewayApplicationTest.java`
- `services/api-gateway/src/test/java/com/gm2dev/api_gateway/SecurityConfigTest.java`
- `services/api-gateway/src/test/resources/application-test.yml`

**Modify:**
- `settings.gradle` — add `include 'services:api-gateway'`
- `compose.yaml` — add `api-gateway` service; remove external port mapping from `app`; add `SERVER_PORT: 8082` to `app`
- `frontend/nginx.conf` — change proxy target from `http://app:8080` to `http://api-gateway:8080`

---

## Task 1: Register api-gateway in the Gradle monorepo

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1: Add api-gateway to settings.gradle**

Open `settings.gradle` and add `include 'services:api-gateway'` alongside the existing includes:

```groovy
rootProject.name = 'interview_hub'

include 'services:core'
include 'services:shared'
include 'services:eureka-server'
include 'services:api-gateway'
```

- [ ] **Step 2: Verify Gradle resolves the new module (empty directory is fine at this stage)**

```bash
mkdir -p services/api-gateway/src/main/java/com/gm2dev/api_gateway
./gradlew projects 2>&1 | grep api-gateway
```

Expected: `+--- Project ':services:api-gateway'` appears in the output.

- [ ] **Step 3: Commit**

```bash
git add settings.gradle services/api-gateway/
git commit -m "chore: register api-gateway module in Gradle monorepo"
```

---

## Task 2: Create api-gateway module scaffold with failing context test

**Files:**
- Create: `services/api-gateway/build.gradle`
- Create: `services/api-gateway/src/test/java/com/gm2dev/api_gateway/ApiGatewayApplicationTest.java`
- Create: `services/api-gateway/src/test/resources/application-test.yml`

- [ ] **Step 1: Write the failing context-load test**

Create `services/api-gateway/src/test/java/com/gm2dev/api_gateway/ApiGatewayApplicationTest.java`:

```java
package com.gm2dev.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the api-gateway Spring context starts without errors.
    }
}
```

- [ ] **Step 2: Create application-test.yml**

Create `services/api-gateway/src/test/resources/application-test.yml`:

```yaml
app:
  jwt:
    signing-secret: test-secret-key-that-is-at-least-32-bytes-long

eureka:
  client:
    enabled: false

spring:
  cloud:
    gateway:
      routes: []  # disable routes in tests — we test security, not routing
```

- [ ] **Step 3: Run the test to confirm it fails (module not buildable yet)**

```bash
./gradlew :services:api-gateway:test 2>&1 | head -30
```

Expected: Compilation failure — `ApiGatewayApplication` does not exist.

- [ ] **Step 4: Create services/api-gateway/build.gradle**

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('bootBuildImage') {
    imageName = "api-gateway:${version}"
    environment = ['BP_JVM_VERSION': '25']
}
```

- [ ] **Step 5: Create ApiGatewayApplication**

Create `services/api-gateway/src/main/java/com/gm2dev/api_gateway/ApiGatewayApplication.java`:

```java
package com.gm2dev.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 6: Create JwtProperties config record**

Create `services/api-gateway/src/main/java/com/gm2dev/api_gateway/config/JwtProperties.java`:

```java
package com.gm2dev.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String signingSecret) {}
```

- [ ] **Step 7: Create application.yml**

Create `services/api-gateway/src/main/resources/application.yml`:

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
          enabled: false  # we define routes explicitly
      routes:
        - id: core-auth
          uri: lb://core
          predicates:
            - Path=/auth/**
        - id: core-actuator
          uri: lb://core
          predicates:
            - Path=/actuator/**
        - id: core-default
          uri: lb://core
          predicates:
            - Path=/**

app:
  jwt:
    signing-secret: ${JWT_SIGNING_SECRET}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

- [ ] **Step 8: Run context test — expect it still fails (SecurityConfig missing)**

```bash
./gradlew :services:api-gateway:test 2>&1 | tail -20
```

Expected: Context fails to load because `app.jwt.signing-secret` is unconfigured or security auto-configuration conflicts. We fix this in Task 3.

---

## Task 3: Implement Gateway SecurityConfig with failing security tests

**Files:**
- Create: `services/api-gateway/src/main/java/com/gm2dev/api_gateway/config/SecurityConfig.java`
- Create: `services/api-gateway/src/test/java/com/gm2dev/api_gateway/SecurityConfigTest.java`

- [ ] **Step 1: Write failing security tests**

Create `services/api-gateway/src/test/java/com/gm2dev/api_gateway/SecurityConfigTest.java`:

```java
package com.gm2dev.api_gateway;

import com.gm2dev.api_gateway.config.JwtProperties;
import com.gm2dev.api_gateway.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest
@Import({SecurityConfig.class})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.jwt.signing-secret=test-secret-key-that-is-at-least-32-bytes-long",
    "eureka.client.enabled=false",
    "spring.cloud.gateway.routes="
})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void authEndpointShouldBePublic() {
        webTestClient.get()
            .uri("/auth/google")
            .exchange()
            .expectStatus().isNotFound(); // 404 is fine — no route registered in test; 401 would be wrong
    }

    @Test
    void actuatorHealthShouldBePublic() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isNotFound(); // 404 = reached the app layer, not blocked by security
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() {
        webTestClient.get()
            .uri("/interviews")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithValidTokenShouldNotReturn401() {
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt())
            .get()
            .uri("/interviews")
            .exchange()
            .expectStatus().isNotFound(); // 404 = passed security, no route in test context
    }
}
```

- [ ] **Step 2: Run failing security tests**

```bash
./gradlew :services:api-gateway:test --tests "com.gm2dev.api_gateway.SecurityConfigTest" 2>&1 | tail -30
```

Expected: Compilation failure or context failure — `SecurityConfig` does not exist yet.

- [ ] **Step 3: Create SecurityConfig**

Create `services/api-gateway/src/main/java/com/gm2dev/api_gateway/config/SecurityConfig.java`:

```java
package com.gm2dev.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.jwt.signing-secret}")
    private String signingSecret;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/auth/**", "/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            )
            .csrf(csrf -> csrf.disable())
            .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
            signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }
}
```

- [ ] **Step 4: Enable @ConfigurationProperties scanning in ApiGatewayApplication**

Update `ApiGatewayApplication.java` to add `@EnableConfigurationProperties`:

```java
package com.gm2dev.api_gateway;

import com.gm2dev.api_gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 5: Run all api-gateway tests — expect all to pass**

```bash
./gradlew :services:api-gateway:test
```

Expected: `ApiGatewayApplicationTest` (1 test) and `SecurityConfigTest` (4 tests) all pass.

- [ ] **Step 6: Commit**

```bash
git add services/api-gateway/
git commit -m "feat: add api-gateway module with JWT security and Eureka routing"
```

---

## Task 4: Update Docker Compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Update compose.yaml**

The changes are:
1. Add `api-gateway` service at port 8080.
2. Remove the external port mapping from `app` (core) — it becomes internal only.
3. Add `SERVER_PORT: 8082` to `app` so it binds on 8082 inside the network.
4. Update `app`'s existing env vars to preserve all existing configuration.
5. Both `app` and `api-gateway` depend on `eureka-server`.

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

  eureka-server:
    image: eureka-server:0.0.1-SNAPSHOT
    ports:
      - "8761:8761"
    environment:
      EUREKA_HOSTNAME: eureka-server
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  api-gateway:
    image: api-gateway:0.0.1-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
      JWT_SIGNING_SECRET: ${JWT_SIGNING_SECRET}
      EUREKA_URL: http://eureka-server:8761/eureka/
    env_file:
      - path: .env
        required: false
    depends_on:
      eureka-server:
        condition: service_healthy

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
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      MAIL_FROM: ${MAIL_FROM:-noreply@lcarera.dev}
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
      EUREKA_URL: http://eureka-server:8761/eureka/
    env_file:
      - path: .env
        required: false
    depends_on:
      eureka-server:
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

Note: `app` no longer has a `ports:` mapping — it is only reachable within the Docker network via `http://app:8082`. The gateway discovers it through Eureka using service name `core`.

- [ ] **Step 2: Commit**

```bash
git add compose.yaml
git commit -m "chore: add api-gateway to compose, move core to internal port 8082"
```

---

## Task 5: Update nginx.conf to proxy to api-gateway

**Files:**
- Modify: `frontend/nginx.conf`

- [ ] **Step 1: Change the proxy_pass targets in nginx.conf**

The current `nginx.conf` proxies `/api/`, `/auth/`, `/admin/`, and `/actuator` to `http://app:8080`. Change each `proxy_pass` to point to `http://api-gateway:8080` instead.

Open `frontend/nginx.conf` and replace every occurrence of `http://app:8080` with `http://api-gateway:8080`.

The relevant proxy blocks look like this after the change:

```nginx
location /api/ {
    proxy_pass http://api-gateway:8080;
    ...
}

location /auth/ {
    proxy_pass http://api-gateway:8080;
    ...
}

location /admin/ {
    proxy_pass http://api-gateway:8080;
    ...
}

location /actuator {
    proxy_pass http://api-gateway:8080;
    ...
}
```

The `/internal/` path must remain NOT proxied — do not add it here. Cloud Tasks calls Cloud Run directly.

- [ ] **Step 2: Verify no remaining references to http://app:8080**

```bash
grep -n "http://app:8080" frontend/nginx.conf
```

Expected: No output (zero matches).

- [ ] **Step 3: Commit**

```bash
git add frontend/nginx.conf
git commit -m "chore: update nginx to proxy API calls to api-gateway instead of app"
```

---

## Task 6: Build image and smoke-test the full stack

- [ ] **Step 1: Build the api-gateway image**

```bash
./gradlew :services:api-gateway:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/api-gateway:0.0.1-SNAPSHOT'`

- [ ] **Step 2: Build the core image (picks up SERVER_PORT change)**

```bash
./gradlew :services:core:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/interview-hub:0.0.1-SNAPSHOT'`

- [ ] **Step 3: Start the full stack**

```bash
docker compose up -d
```

- [ ] **Step 4: Verify Eureka shows both core and api-gateway registered**

Open http://localhost:8761 in a browser.

Expected: Both `API-GATEWAY` and `CORE` appear under "Instances currently registered with Eureka" with status UP.

- [ ] **Step 5: Verify the health endpoint is reachable through the gateway**

```bash
curl -s http://localhost:8080/actuator/health | jq .status
```

Expected: `"UP"` — the request flows: curl → gateway (port 8080) → lb://core → core at 8082.

- [ ] **Step 6: Verify a protected endpoint returns 401 without a token**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/interviews
```

Expected: `401`

- [ ] **Step 7: Verify a protected endpoint is accessible with a valid JWT**

Obtain a token via the Postman collection or:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -d "code=<google_auth_code>&redirect_uri=<your_redirect_uri>" | jq -r .access_token)

curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/interviews
```

Expected: `200` (or `404` if no interviews exist — either means the request passed through security).

- [ ] **Step 8: Verify core is NOT directly reachable from outside Docker**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health
```

Expected: Connection refused — port 8082 is not exposed on the host.

- [ ] **Step 9: Commit**

```bash
git add .
git commit -m "chore: verify api-gateway full-stack smoke test passes"
```

---

## Task 7: Deploy API Gateway to GCP Cloud Run

**Files:**
- Modify: `infra/cloudrun.py`
- Modify: `infra/__main__.py`
- Modify: `.github/workflows/deploy.yml`

> The api-gateway becomes the new public entry point for all API traffic. The frontend and Cloudflare DNS already point at `backend_domain` — after this task, that domain serves the gateway instead of core. Core stays publicly accessible at its own Cloud Run URL for now (acceptable for a learning project; in production you'd restrict core to `INGRESS_TRAFFIC_INTERNAL_ONLY`).
>
> The gateway validates HMAC-SHA256 JWTs, so it needs `JWT_SIGNING_SECRET` from Secret Manager.
>
> **OAuth callback note:** `APP_BASE_URL` on core is used to build the Google OAuth redirect URI (`APP_BASE_URL/auth/google/callback`). The gateway routes `/auth/**` to core, so this URI goes through the gateway. After this task, `APP_BASE_URL` on core must equal the gateway's public domain (same value as `backend_domain`). Update `Pulumi.prod.yaml`'s `interview-hub-infra:backend_domain` to point to the gateway's Cloudflare DNS entry.

- [ ] **Step 1: Update `infra/cloudrun.py`**

**1a.** Add the gateway image config variable:

```python
gateway_image = config.get("gateway_image") or "placeholder"
```

**1b.** Add `api_gateway_service` after `calendar_service`. The gateway needs `JWT_SIGNING_SECRET` from Secret Manager and `EUREKA_URL` as a plain env var:

```python
_gateway_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name="JWT_SIGNING_SECRET",
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secrets["JWT_SIGNING_SECRET"].secret_id,
                version="latest",
            )
        ),
    ),
]

api_gateway_service = gcp.cloudrunv2.Service(
    "api-gateway",
    name="interview-hub-api-gateway",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=0),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=gateway_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8080)],
                envs=_gateway_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="EUREKA_URL",
                        value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="APP_BASE_URL",
                        value=pulumi.Output.concat("https://", backend_domain),
                    ),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
                ),
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8080,
                    ),
                    initial_delay_seconds=15,
                    period_seconds=5,
                    failure_threshold=20,
                ),
            )
        ],
    ),
)

gcp.cloudrunv2.ServiceIamMember(
    "api-gateway-invoker",
    project=project,
    location=region,
    name=api_gateway_service.name,
    role="roles/run.invoker",
    member="allUsers",
)
```

**1c.** On `backend_service`, change `APP_BASE_URL` to use the gateway domain instead of the backend domain:

```python
# Change this:
gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
    name="APP_BASE_URL",
    value=pulumi.Output.concat("https://", backend_domain),
),
# To this (core's APP_BASE_URL = gateway domain so OAuth callbacks go through the gateway):
gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
    name="APP_BASE_URL",
    value=pulumi.Output.concat("https://", backend_domain),  # backend_domain now points to gateway via DNS
),
```

(No code change needed — `backend_domain` in `Pulumi.prod.yaml` will be updated to the gateway's Cloudflare DNS entry.)

- [ ] **Step 2: Export gateway URL in `infra/__main__.py`**

```python
from cloudrun import backend_service, frontend_service, eureka_service, notification_service, calendar_service, api_gateway_service
# ...
pulumi.export("gateway_url", api_gateway_service.uri)
```

- [ ] **Step 3: Update `.github/workflows/deploy.yml`**

**3a.** Add `gateway` filter and output:

```yaml
outputs:
  # ... existing outputs ...
  gateway: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.gateway }}
```

```yaml
filters: |
  # ... existing filters ...
  gateway:
    - 'services/api-gateway/**'
```

**3b.** Add gateway to the `deploy` job `if` condition:

```yaml
if: ... || needs.changes.outputs.gateway == 'true'
```

**3c.** Add build-and-push step:

```yaml
- name: Build and push gateway image
  if: needs.changes.outputs.gateway == 'true'
  run: |
    IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/api-gateway:${{ github.sha }}
    ./gradlew :services:api-gateway:bootBuildImage --imageName=$IMAGE
    docker push $IMAGE
```

**3d.** In the "Deploy with Pulumi" step, add gateway image config:

```bash
if [ "${{ needs.changes.outputs.gateway }}" == "true" ]; then
  pulumi config set interview-hub-infra:gateway_image \
    ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/api-gateway:${{ github.sha }}
else
  CURRENT=$(gcloud run services describe interview-hub-api-gateway \
    --region=${{ env.GCP_REGION }} --format='value(spec.template.spec.containers[0].image)' 2>/dev/null || echo "")
  if [ -n "$CURRENT" ]; then
    pulumi config set interview-hub-infra:gateway_image "$CURRENT"
  fi
fi
```

- [ ] **Step 4: Update `Pulumi.prod.yaml` `backend_domain` to point to the gateway**

After deploying the gateway, update Cloudflare DNS for `backend_domain` to point at the gateway's Cloud Run URL. In `Pulumi.prod.yaml`, no change is needed to `backend_domain` if you're reusing the same Cloudflare entry — just update the DNS target.

If you want a separate domain for the gateway (e.g. `i-hub-gw.lcarera.dev`), add a new `gateway_domain` config key and update `APP_BASE_URL` on both core and gateway accordingly.

- [ ] **Step 5: Verify with `pulumi preview`**

```bash
cd infra
source venv/bin/activate
pulumi stack select prod
pulumi preview
```

Expected: Preview shows `+ interview-hub-api-gateway` to create. No errors.

- [ ] **Step 6: Commit**

```bash
git add infra/cloudrun.py infra/__main__.py .github/workflows/deploy.yml
git commit -m "feat: deploy api-gateway to Cloud Run"
```

---

## Self-Review

**Spec coverage:**
- All `/auth/**` and `/actuator/**` traffic reaches core without JWT validation at the gateway.
- All other traffic requires a valid HMAC-SHA256 JWT at the gateway level (defense in depth — core validates too).
- `core` is no longer exposed externally; only reachable via `lb://core` inside the Docker network.
- `api-gateway` registers with Eureka and resolves `core` by service name.
- `nginx` proxies to `http://api-gateway:8080` for all API paths; `/internal/**` remains unexposed.
- TDD: failing tests written before each implementation step, all tests green before commit.
- `application-test.yml` disables Eureka and routes in tests to keep them fast and hermetic.

**What this plan does NOT do** (handled in subsequent plans):
- Route-level `ROLE_admin` enforcement at the gateway — currently delegated to core's `@PreAuthorize`. Can be added to `SecurityConfig` with `.pathMatchers("/admin/**").hasRole("admin")` once the JWT converter for custom claims is added.
- Rate limiting, circuit breaking, or request tracing — gateway infrastructure concerns for later.
- calendar-service and notification-service routes — those services do not expose HTTP APIs consumed by the frontend; they are internal and routed via messaging (Plans 2 and 3).
