# Microservices Migration — Plan 1: Monorepo + Shared Module + Eureka Server

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the existing single-module Gradle project into a multi-module monorepo, add a `shared` DTOs module containing inter-service contracts, and stand up a Eureka service registry that `core` registers with.

**Architecture:** The root `build.gradle` holds shared Gradle conventions (Java toolchain, repositories, BOMs) applied to all subprojects. `services/core` is the existing backend moved into its own module. `services/shared` is a plain Java library with sealed interfaces and records for inter-service communication. `services/eureka-server` is a minimal Spring Boot app with `@EnableEurekaServer`.

**Tech Stack:** Gradle multi-module, Spring Boot 4.0.2, Spring Cloud (see compatibility note below), Netflix Eureka, Java 25.

> **Spring Cloud Compatibility Note:** This plan uses Spring Cloud `2025.0.0`. Since Spring Boot 4.0.2 was released after Spring Cloud `2024.0.x` (which targets 3.4.x), verify the compatible release at https://spring.io/projects/spring-cloud#learn and substitute the correct version if `2025.0.0` does not resolve.

---

## File Map

**Create:**
- `services/core/build.gradle` — core-specific Gradle config (moved+edited from root `build.gradle`)
- `services/core/src/` — existing backend source (moved from root `src/`)
- `services/shared/build.gradle` — plain Java library, Jackson only
- `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventRequest.java`
- `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventResponse.java`
- `services/shared/src/main/java/com/gm2dev/shared/calendar/AttendeeRequest.java`
- `services/shared/src/main/java/com/gm2dev/shared/email/EmailMessage.java`
- `services/shared/src/test/java/com/gm2dev/shared/EmailMessageSerializationTest.java`
- `services/shared/src/test/java/com/gm2dev/shared/CalendarDtoSerializationTest.java`
- `services/eureka-server/build.gradle`
- `services/eureka-server/src/main/java/com/gm2dev/eureka_server/EurekaServerApplication.java`
- `services/eureka-server/src/main/resources/application.yml`
- `services/eureka-server/src/test/java/com/gm2dev/eureka_server/EurekaServerApplicationTest.java`

**Modify:**
- `settings.gradle` — declare multi-module structure
- `build.gradle` (root) — replace with common subprojects config
- `compose.yaml` — add `eureka-server` and `rabbitmq` services; update `app` with `EUREKA_URL`
- `services/core/src/main/resources/application.yml` — add Eureka client config

---

## Task 1: Restructure to multi-module Gradle monorepo

**Files:**
- Create: `services/core/` directory
- Move: `src/` → `services/core/src/`
- Create: `services/core/build.gradle`
- Modify: `settings.gradle`
- Replace: `build.gradle` (root)

- [ ] **Step 1: Create the services/core directory and move existing source**

```bash
mkdir -p services/core
mv src services/core/src
```

- [ ] **Step 2: Create services/core/build.gradle**

This is the existing `build.gradle` content, adapted for a submodule (removes `group`/`version`/`java toolchain` which move to root, keeps core-specific plugins and dependencies):

```groovy
plugins {
    id 'jacoco'
    id 'org.springframework.boot'
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

dependencies {
    implementation project(':services:shared')

    // Web
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'

    // Data & Database
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.postgresql:postgresql'

    // Security
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Email (Resend HTTP API)
    implementation 'com.resend:resend-java:4.11.0'

    // Actuator (health checks, metrics)
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // API Documentation
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2'

    // JSON Processing
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    // Google Cloud BOM
    implementation platform('com.google.cloud:libraries-bom:26.77.0')

    // Google API (Calendar + Auth)
    implementation 'com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0'
    implementation 'com.google.api-client:google-api-client'
    implementation 'com.google.http-client:google-http-client-jackson2'
    implementation 'com.google.auth:google-auth-library-oauth2-http'

    // Google Cloud Tasks
    implementation 'com.google.cloud:google-cloud-tasks'

    // Eureka Client
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // MapStruct
    implementation 'org.mapstruct:mapstruct:1.6.3'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

    // Development Tools
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    developmentOnly 'org.springframework.boot:spring-boot-docker-compose'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/InterviewHubApplication.class',
                '**/GoogleCalendarService.class',
                '**/OpenApiConfig.class',
                '**/*MapperImpl.class',
                '**/CloudTasksConfig.class',
                '**/CloudTasksAuthenticationFilter.class',
                '**/CloudTasksSecurityConfig.class',
                '**/SecurityConfig.class',
                '**/EmailQueueService.class'
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/InterviewHubApplication.class',
                '**/GoogleCalendarService.class',
                '**/OpenApiConfig.class',
                '**/*MapperImpl.class',
                '**/CloudTasksConfig.class',
                '**/CloudTasksAuthenticationFilter.class',
                '**/CloudTasksSecurityConfig.class',
                '**/SecurityConfig.class',
                '**/EmailQueueService.class'
            ])
        }))
    }
    violationRules {
        rule {
            limit {
                counter = 'BRANCH'
                minimum = 0.95
            }
        }
    }
}

tasks.named('check') {
    dependsOn jacocoTestCoverageVerification
}

tasks.named('bootBuildImage') {
    imageName = "interview-hub:${version}"
    environment = [
        'BP_JVM_VERSION': '25'
    ]
}
```

- [ ] **Step 3: Replace root build.gradle with common subprojects config**

Overwrite `build.gradle` in the project root with:

```groovy
plugins {
    id 'java' apply false
    id 'org.springframework.boot' version '4.0.2' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'com.gm2dev'
    version = '0.0.1-SNAPSHOT'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
    }

    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.2"
            mavenBom "org.springframework.cloud:spring-cloud-dependencies:2025.0.0"
        }
    }

    tasks.withType(Test).configureEach {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 4: Update root settings.gradle**

Replace `settings.gradle` with:

```groovy
rootProject.name = 'interview_hub'

include 'services:core'
include 'services:shared'
include 'services:eureka-server'
```

- [ ] **Step 5: Verify core tests still pass**

```bash
./gradlew :services:core:test
```

Expected: All existing tests pass. If any test references a class not found, check that the source move completed (`services/core/src/` should have `main/` and `test/` subdirectories).

- [ ] **Step 6: Commit**

```bash
git add services/core/ settings.gradle build.gradle
git commit -m "chore: restructure to multi-module Gradle monorepo"
```

---

## Task 2: Create shared module with inter-service DTOs

**Files:**
- Create: `services/shared/build.gradle`
- Create: `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventRequest.java`
- Create: `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventResponse.java`
- Create: `services/shared/src/main/java/com/gm2dev/shared/calendar/AttendeeRequest.java`
- Create: `services/shared/src/main/java/com/gm2dev/shared/email/EmailMessage.java`
- Create: `services/shared/src/test/java/com/gm2dev/shared/EmailMessageSerializationTest.java`
- Create: `services/shared/src/test/java/com/gm2dev/shared/CalendarDtoSerializationTest.java`

- [ ] **Step 1: Write the failing tests for EmailMessage serialization**

Create `services/shared/src/test/java/com/gm2dev/shared/EmailMessageSerializationTest.java`:

```java
package com.gm2dev.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.shared.email.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMessageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldRoundTripVerificationEmail() throws Exception {
        var original = new EmailMessage.VerificationEmailMessage("user@example.com", "tok123");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.VerificationEmailMessage.class);
        var typed = (EmailMessage.VerificationEmailMessage) result;
        assertThat(typed.to()).isEqualTo("user@example.com");
        assertThat(typed.token()).isEqualTo("tok123");
    }

    @Test
    void shouldRoundTripPasswordResetEmail() throws Exception {
        var original = new EmailMessage.PasswordResetEmailMessage("reset@example.com", "reset-tok");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.PasswordResetEmailMessage.class);
        assertThat(((EmailMessage.PasswordResetEmailMessage) result).to()).isEqualTo("reset@example.com");
    }

    @Test
    void shouldRoundTripTemporaryPasswordEmail() throws Exception {
        var original = new EmailMessage.TemporaryPasswordEmailMessage("new@example.com", "TmpPass1!");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.TemporaryPasswordEmailMessage.class);
        assertThat(((EmailMessage.TemporaryPasswordEmailMessage) result).temporaryPassword()).isEqualTo("TmpPass1!");
    }

    @Test
    void shouldRoundTripShadowingApprovedEmail() throws Exception {
        var original = new EmailMessage.ShadowingApprovedEmailMessage(
            "shadow@example.com", "Java Interview - Alice", "2026-04-01T10:00", "2026-04-01T11:00");
        String json = mapper.writeValueAsString(original);
        EmailMessage result = mapper.readValue(json, EmailMessage.class);
        assertThat(result).isInstanceOf(EmailMessage.ShadowingApprovedEmailMessage.class);
        var typed = (EmailMessage.ShadowingApprovedEmailMessage) result;
        assertThat(typed.summary()).isEqualTo("Java Interview - Alice");
    }

    @Test
    void serializedJsonShouldContainTypeDiscriminator() throws Exception {
        var msg = new EmailMessage.VerificationEmailMessage("u@e.com", "t");
        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"type\":\"VERIFICATION\"");
    }
}
```

- [ ] **Step 2: Write the failing tests for CalendarEventRequest/Response serialization**

Create `services/shared/src/test/java/com/gm2dev/shared/CalendarDtoSerializationTest.java`:

```java
package com.gm2dev.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarDtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldRoundTripCalendarEventRequest() throws Exception {
        var req = new CalendarEventRequest(
            null, "Java", "Alice", "alice@example.com",
            "https://linkedin.com/in/alice", "Backend", "https://feedback.link",
            "interviewer@example.com", List.of("shadow@example.com"),
            Instant.parse("2026-04-01T10:00:00Z"), Instant.parse("2026-04-01T11:00:00Z")
        );
        String json = mapper.writeValueAsString(req);
        CalendarEventRequest result = mapper.readValue(json, CalendarEventRequest.class);
        assertThat(result.candidateName()).isEqualTo("Alice");
        assertThat(result.approvedShadowerEmails()).containsExactly("shadow@example.com");
        assertThat(result.googleEventId()).isNull();
    }

    @Test
    void shouldRoundTripCalendarEventResponse() throws Exception {
        var resp = new CalendarEventResponse("evt-123", "https://meet.google.com/abc");
        String json = mapper.writeValueAsString(resp);
        CalendarEventResponse result = mapper.readValue(json, CalendarEventResponse.class);
        assertThat(result.eventId()).isEqualTo("evt-123");
        assertThat(result.meetLink()).isEqualTo("https://meet.google.com/abc");
    }

    @Test
    void shouldRoundTripAttendeeRequest() throws Exception {
        var req = new AttendeeRequest("evt-456", "newattendee@example.com");
        String json = mapper.writeValueAsString(req);
        AttendeeRequest result = mapper.readValue(json, AttendeeRequest.class);
        assertThat(result.googleEventId()).isEqualTo("evt-456");
        assertThat(result.email()).isEqualTo("newattendee@example.com");
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail (class not found)**

```bash
./gradlew :services:shared:test 2>&1 | head -30
```

Expected: Compilation failure — `com.gm2dev.shared.email.EmailMessage` does not exist.

- [ ] **Step 4: Create services/shared/build.gradle**

```groovy
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'

    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 5: Create EmailMessage sealed interface**

Create `services/shared/src/main/java/com/gm2dev/shared/email/EmailMessage.java`:

```java
package com.gm2dev.shared.email;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailMessage.VerificationEmailMessage.class, name = "VERIFICATION"),
        @JsonSubTypes.Type(value = EmailMessage.PasswordResetEmailMessage.class, name = "PASSWORD_RESET"),
        @JsonSubTypes.Type(value = EmailMessage.TemporaryPasswordEmailMessage.class, name = "TEMPORARY_PASSWORD"),
        @JsonSubTypes.Type(value = EmailMessage.ShadowingApprovedEmailMessage.class, name = "SHADOWING_APPROVED")
})
public sealed interface EmailMessage permits
        EmailMessage.VerificationEmailMessage,
        EmailMessage.PasswordResetEmailMessage,
        EmailMessage.TemporaryPasswordEmailMessage,
        EmailMessage.ShadowingApprovedEmailMessage {

    String to();

    record VerificationEmailMessage(String to, String token) implements EmailMessage {}

    record PasswordResetEmailMessage(String to, String token) implements EmailMessage {}

    record TemporaryPasswordEmailMessage(String to, String temporaryPassword) implements EmailMessage {}

    record ShadowingApprovedEmailMessage(
            String to,
            String summary,
            String startTime,
            String endTime
    ) implements EmailMessage {}
}
```

- [ ] **Step 6: Create CalendarEventRequest record**

Create `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventRequest.java`:

```java
package com.gm2dev.shared.calendar;

import java.time.Instant;
import java.util.List;

/**
 * Sent by core to calendar-service when creating or updating an interview event.
 * googleEventId is null for create operations, required for update operations.
 */
public record CalendarEventRequest(
        String googleEventId,
        String techStack,
        String candidateName,
        String candidateEmail,
        String candidateLinkedIn,
        String primaryArea,
        String feedbackLink,
        String interviewerEmail,
        List<String> approvedShadowerEmails,
        Instant startTime,
        Instant endTime
) {}
```

- [ ] **Step 7: Create CalendarEventResponse record**

Create `services/shared/src/main/java/com/gm2dev/shared/calendar/CalendarEventResponse.java`:

```java
package com.gm2dev.shared.calendar;

/**
 * Returned by calendar-service after creating or updating an event.
 * meetLink may be null if no conference was created.
 */
public record CalendarEventResponse(String eventId, String meetLink) {}
```

- [ ] **Step 8: Create AttendeeRequest record**

Create `services/shared/src/main/java/com/gm2dev/shared/calendar/AttendeeRequest.java`:

```java
package com.gm2dev.shared.calendar;

/**
 * Sent by core to calendar-service to add or remove a single attendee from an event.
 */
public record AttendeeRequest(String googleEventId, String email) {}
```

- [ ] **Step 9: Run tests to confirm they pass**

```bash
./gradlew :services:shared:test
```

Expected: `EmailMessageSerializationTest` — 5 tests pass. `CalendarDtoSerializationTest` — 3 tests pass.

- [ ] **Step 10: Verify core still compiles with shared dependency**

```bash
./gradlew :services:core:compileJava
```

Expected: BUILD SUCCESSFUL (the `shared` dependency declared in `services/core/build.gradle` resolves correctly).

- [ ] **Step 11: Commit**

```bash
git add services/shared/
git commit -m "feat: add shared module with inter-service calendar and email DTOs"
```

---

## Task 3: Create Eureka Server

**Files:**
- Create: `services/eureka-server/build.gradle`
- Create: `services/eureka-server/src/main/java/com/gm2dev/eureka_server/EurekaServerApplication.java`
- Create: `services/eureka-server/src/main/resources/application.yml`
- Create: `services/eureka-server/src/test/java/com/gm2dev/eureka_server/EurekaServerApplicationTest.java`
- Modify: `services/core/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing Eureka server context test**

Create `services/eureka-server/src/test/java/com/gm2dev/eureka_server/EurekaServerApplicationTest.java`:

```java
package com.gm2dev.eureka_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EurekaServerApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Eureka server Spring context starts without errors.
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails (class not found)**

```bash
./gradlew :services:eureka-server:test 2>&1 | head -20
```

Expected: Compilation failure — `EurekaServerApplication` does not exist.

- [ ] **Step 3: Create services/eureka-server/build.gradle**

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('bootBuildImage') {
    imageName = "eureka-server:${version}"
    environment = [
        'BP_JVM_VERSION': '25'
    ]
}
```

- [ ] **Step 4: Create EurekaServerApplication**

Create `services/eureka-server/src/main/java/com/gm2dev/eureka_server/EurekaServerApplication.java`:

```java
package com.gm2dev.eureka_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

- [ ] **Step 5: Create eureka-server application.yml**

Create `services/eureka-server/src/main/resources/application.yml`:

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: ${EUREKA_HOSTNAME:localhost}
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 6: Create application-test.yml for eureka-server (prevents peer replication noise in tests)**

Create `services/eureka-server/src/test/resources/application-test.yml`:

```yaml
eureka:
  server:
    enable-self-preservation: false
  client:
    register-with-eureka: false
    fetch-registry: false
```

- [ ] **Step 7: Run the Eureka server test**

```bash
./gradlew :services:eureka-server:test
```

Expected: `EurekaServerApplicationTest` — 1 test passes (`contextLoads`).

- [ ] **Step 8: Add Eureka client config to core's application.yml**

Add to `services/core/src/main/resources/application.yml` (append after existing content):

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true
```

Also update the `spring.application.name` in core's `application.yml` — change it from `interview-hub` to `core` (Eureka uses this as the service name):

```yaml
spring:
  application:
    name: core
```

- [ ] **Step 9: Verify core tests still pass after application.yml change**

```bash
./gradlew :services:core:test
```

Expected: All tests pass. The Eureka client config is harmless in tests — it will attempt connection and fail gracefully since no Eureka server runs in the test profile.

If tests fail with Eureka connection errors, add to `services/core/src/test/resources/application-test.yml`:

```yaml
eureka:
  client:
    enabled: false
```

- [ ] **Step 10: Commit**

```bash
git add services/eureka-server/ services/core/src/main/resources/application.yml
git commit -m "feat: add Eureka server and register core as Eureka client"
```

---

## Task 4: Update Docker Compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Replace compose.yaml with updated version including eureka-server and rabbitmq**

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

  app:
    image: interview-hub:0.0.1-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
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
      - app
```

- [ ] **Step 2: Build the eureka-server image**

```bash
./gradlew :services:eureka-server:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/eureka-server:0.0.1-SNAPSHOT'`

- [ ] **Step 3: Build the core image**

```bash
./gradlew :services:core:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/interview-hub:0.0.1-SNAPSHOT'`

- [ ] **Step 4: Start the stack and verify Eureka dashboard**

```bash
docker compose up eureka-server app -d
```

Then open http://localhost:8761 in a browser.

Expected: Eureka dashboard shows `CORE` listed under "Instances currently registered with Eureka" with status UP.

- [ ] **Step 5: Verify RabbitMQ management UI is accessible**

Open http://localhost:15672 in a browser (default credentials: `guest` / `guest`).

Expected: RabbitMQ management dashboard loads. No queues yet — they'll be created in Plan 2.

- [ ] **Step 6: Commit**

```bash
git add compose.yaml
git commit -m "chore: add eureka-server and rabbitmq to docker compose"
```

---

## Task 5: Deploy Eureka Server to GCP Cloud Run

**Files:**
- Modify: `infra/cloudrun.py`
- Modify: `infra/__main__.py`
- Modify: `.github/workflows/deploy.yml`

> **Why always-on:** Eureka uses a heartbeat model — if the server scales to zero, all registrations are lost and clients enter error state. `min_instance_count=1` keeps it alive at ~$5/month.

- [ ] **Step 1: Add eureka_service to `infra/cloudrun.py`**

Define the eureka image config variable and the Cloud Run service. Place the `eureka_service` definition **before** `backend_service` (it must exist before we can reference `eureka_service.uri`):

```python
eureka_image = config.get("eureka_image") or "placeholder"

eureka_service = gcp.cloudrunv2.Service(
    "eureka-server",
    name="interview-hub-eureka-server",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=1),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=eureka_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8761)],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
                ),
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8761,
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
    "eureka-server-invoker",
    project=project,
    location=region,
    name=eureka_service.name,
    role="roles/run.invoker",
    member="allUsers",
)
```

- [ ] **Step 2: Add `EUREKA_URL` to `backend_service` env vars**

Inside `backend_service`'s `envs=_secret_envs + [...]` list, add:

```python
gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
    name="EUREKA_URL",
    value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
),
```

- [ ] **Step 3: Export eureka URL in `infra/__main__.py`**

```python
from cloudrun import backend_service, frontend_service, eureka_service
# ...
pulumi.export("eureka_url", eureka_service.uri)
```

- [ ] **Step 4: Update `.github/workflows/deploy.yml`**

**4a.** Add `eureka` to the `filters` in the `changes` job and add it to `outputs`:

```yaml
outputs:
  backend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.backend }}
  frontend: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.frontend }}
  infra: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.infra }}
  migrations: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.migrations }}
  eureka: ${{ inputs.force_full_deploy == true && 'true' || steps.filter.outputs.eureka }}
  base_sha: ${{ steps.base.outputs.sha }}
```

```yaml
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
  eureka:
    - 'services/eureka-server/**'
```

**4b.** Update the `deploy` job `if` condition to include eureka:

```yaml
if: needs.changes.outputs.backend == 'true' || needs.changes.outputs.frontend == 'true' || needs.changes.outputs.infra == 'true' || needs.changes.outputs.migrations == 'true' || needs.changes.outputs.eureka == 'true'
```

**4c.** Add a build-and-push step for the eureka image (after the backend build step):

```yaml
- name: Build and push eureka image
  if: needs.changes.outputs.eureka == 'true'
  run: |
    IMAGE=${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/eureka-server:${{ github.sha }}
    ./gradlew :services:eureka-server:bootBuildImage --imageName=$IMAGE
    docker push $IMAGE
```

**4d.** In the "Deploy with Pulumi" step, add eureka image config before `pulumi up`:

```bash
if [ "${{ needs.changes.outputs.eureka }}" == "true" ]; then
  pulumi config set interview-hub-infra:eureka_image \
    ${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/interview-hub/eureka-server:${{ github.sha }}
else
  CURRENT=$(gcloud run services describe interview-hub-eureka-server \
    --region=${{ env.GCP_REGION }} --format='value(spec.template.spec.containers[0].image)' 2>/dev/null || echo "")
  if [ -n "$CURRENT" ]; then
    pulumi config set interview-hub-infra:eureka_image "$CURRENT"
  fi
fi
```

- [ ] **Step 5: Verify with `pulumi preview`**

```bash
cd infra
source venv/bin/activate
pulumi stack select prod
pulumi preview
```

Expected: Preview shows `+ interview-hub-eureka-server` Cloud Run service to create and `~ interview-hub-backend` to update (new `EUREKA_URL` env var). No errors.

- [ ] **Step 6: Commit**

```bash
git add infra/cloudrun.py infra/__main__.py .github/workflows/deploy.yml
git commit -m "feat: deploy eureka-server to Cloud Run; add EUREKA_URL to core"
```

---

## Self-Review

**Spec coverage:**
- ✅ Monorepo multi-module Gradle structure
- ✅ `shared` module with calendar DTOs (`CalendarEventRequest`, `CalendarEventResponse`, `AttendeeRequest`)
- ✅ `shared` module with email event (`EmailMessage` sealed interface matching existing `EmailTaskPayload` subtypes)
- ✅ Eureka server with `@EnableEurekaServer`
- ✅ `core` registers with Eureka
- ✅ RabbitMQ added to compose (needed by Plan 2)
- ✅ TDD for both shared DTOs and Eureka context load
- ✅ Existing core tests verified at every structural change

**What this plan does NOT do** (handled in subsequent plans):
- Plan 2: Extract notification-service; wire `core` to publish `EmailMessage` to RabbitMQ; delete Cloud Tasks code
- Plan 3: Extract calendar-service; wire `core` to call calendar-service via OpenFeign
- Plan 4: Add Spring Cloud Gateway; migrate nginx API routing to Gateway
