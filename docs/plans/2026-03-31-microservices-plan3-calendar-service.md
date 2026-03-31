# Microservices Migration — Plan 3: Extract calendar-service

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Prerequisite:** Plan 1 (monorepo + shared + Eureka) must be completed. All paths below use the post-Plan-1 structure where the backend lives at `services/core/src/...`.

**Goal:** Extract `GoogleCalendarService` from `services/core` into a new stateless `calendar-service` Spring Boot microservice. `core` replaces its direct Google Calendar dependency with an OpenFeign client that calls `calendar-service` by Eureka service name. The shared DTOs (`CalendarEventRequest`, `CalendarEventResponse`, `AttendeeRequest`) created in Plan 1 are the contract between the two services.

**Architecture:** `calendar-service` owns all Google Calendar API calls. It exposes a REST API, registers with Eureka, and is stateless (no database). `core` gains a Feign interface pointing at `calendar-service` by service name. All calendar failures remain non-blocking — the existing `try/catch` wrappers in `InterviewService` and `ShadowingRequestService` catch Feign exceptions exactly as they currently catch `IOException`.

**Tech Stack:** Spring Boot 4.0.2, Spring Cloud OpenFeign, Netflix Eureka client, Google Calendar API v3, Java 25, Gradle multi-module.

---

## File Map

**Create (calendar-service):**
- `services/calendar-service/build.gradle`
- `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarServiceApplication.java`
- `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarController.java`
- `services/calendar-service/src/main/java/com/gm2dev/calendar_service/GoogleCalendarService.java`
- `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleCalendarProperties.java`
- `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleOAuthProperties.java`
- `services/calendar-service/src/main/resources/application.yml`
- `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarServiceApplicationTest.java`
- `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarControllerTest.java`
- `services/calendar-service/src/test/resources/application-test.yml`

**Create (core):**
- `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java`

**Modify:**
- `settings.gradle` — add `services:calendar-service`
- `services/core/build.gradle` — remove Google Calendar deps, add OpenFeign
- `services/core/src/main/java/com/gm2dev/interview_hub/InterviewHubApplication.java` — add `@EnableFeignClients`
- `services/core/src/main/java/com/gm2dev/interview_hub/service/InterviewService.java` — swap `GoogleCalendarService` → `CalendarServiceClient`
- `services/core/src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java` — swap `GoogleCalendarService` → `CalendarServiceClient`
- `services/core/src/main/resources/application.yml` — remove `app.google.calendar` block
- `services/core/src/test/resources/application-test.yml` — remove `app.google.calendar` block
- `services/core/src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java` — mock `CalendarServiceClient` instead of `GoogleCalendarService`
- `services/core/src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java` — mock `CalendarServiceClient` instead of `GoogleCalendarService`
- `compose.yaml` — add `calendar-service` service

**Delete (core):**
- `services/core/src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`
- `services/core/src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java`
- `services/core/src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java`

---

## Task 1: Register calendar-service in the monorepo

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1: Add calendar-service to settings.gradle**

Open `settings.gradle` and add the new module:

```groovy
rootProject.name = 'interview_hub'

include 'services:core'
include 'services:shared'
include 'services:eureka-server'
include 'services:calendar-service'
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p services/calendar-service/src/main/java/com/gm2dev/calendar_service/config
mkdir -p services/calendar-service/src/main/resources
mkdir -p services/calendar-service/src/test/java/com/gm2dev/calendar_service
mkdir -p services/calendar-service/src/test/resources
```

- [ ] **Step 3: Verify Gradle recognizes the new module**

```bash
./gradlew projects 2>&1 | grep calendar
```

Expected output includes `--- Project ':services:calendar-service'`.

---

## Task 2: Write failing tests for calendar-service (TDD)

**Files:**
- Create: `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarServiceApplicationTest.java`
- Create: `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarControllerTest.java`
- Create: `services/calendar-service/src/test/resources/application-test.yml`

- [ ] **Step 1: Write the application context smoke test**

Create `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarServiceApplicationTest.java`:

```java
package com.gm2dev.calendar_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CalendarServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts with mocked Google Calendar config.
    }
}
```

- [ ] **Step 2: Write controller tests**

Create `services/calendar-service/src/test/java/com/gm2dev/calendar_service/CalendarControllerTest.java`:

```java
package com.gm2dev.calendar_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private CalendarEventRequest buildRequest(String googleEventId) {
        return new CalendarEventRequest(
                googleEventId,
                "Java",
                "Alice Smith",
                "alice@example.com",
                "https://linkedin.com/in/alice",
                "Backend",
                "https://feedback.link/123",
                "interviewer@gm2dev.com",
                List.of("shadow@gm2dev.com"),
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T11:00:00Z")
        );
    }

    @Test
    void postEvents_createsEventAndReturnsResponse() throws Exception {
        CalendarEventRequest request = buildRequest(null);
        CalendarEventResponse response = new CalendarEventResponse("evt-abc123", "https://meet.google.com/xyz");
        when(googleCalendarService.createEvent(any())).thenReturn(response);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-abc123"))
                .andExpect(jsonPath("$.meetLink").value("https://meet.google.com/xyz"));

        verify(googleCalendarService).createEvent(any());
    }

    @Test
    void putEventsEventId_updatesEventAndReturns204() throws Exception {
        CalendarEventRequest request = buildRequest("evt-abc123");
        doNothing().when(googleCalendarService).updateEvent(any());

        mockMvc.perform(put("/events/evt-abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).updateEvent(any());
    }

    @Test
    void deleteEventsEventId_deletesEventAndReturns204() throws Exception {
        doNothing().when(googleCalendarService).deleteEvent(eq("evt-abc123"));

        mockMvc.perform(delete("/events/evt-abc123"))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).deleteEvent("evt-abc123");
    }

    @Test
    void postEventsEventIdAttendees_addsAttendeeAndReturns204() throws Exception {
        AttendeeRequest attendeeRequest = new AttendeeRequest("evt-abc123", "shadow@gm2dev.com");
        doNothing().when(googleCalendarService).addAttendee(any());

        mockMvc.perform(post("/events/evt-abc123/attendees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(attendeeRequest)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).addAttendee(any());
    }

    @Test
    void deleteEventsEventIdAttendees_removesAttendeeAndReturns204() throws Exception {
        AttendeeRequest attendeeRequest = new AttendeeRequest("evt-abc123", "shadow@gm2dev.com");
        doNothing().when(googleCalendarService).removeAttendee(any());

        mockMvc.perform(delete("/events/evt-abc123/attendees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(attendeeRequest)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).removeAttendee(any());
    }

    @Test
    void postEvents_whenCalendarServiceThrows_returns500() throws Exception {
        CalendarEventRequest request = buildRequest(null);
        when(googleCalendarService.createEvent(any())).thenThrow(new RuntimeException("Google Calendar unavailable"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}
```

- [ ] **Step 3: Create test application.yml**

Create `services/calendar-service/src/test/resources/application-test.yml`:

```yaml
app:
  google:
    client-id: fake-client-id
    client-secret: fake-client-secret
    calendar:
      id: primary
      refresh-token: fake-refresh-token

eureka:
  client:
    enabled: false

logging:
  level:
    com.gm2dev.calendar_service: DEBUG
```

- [ ] **Step 4: Run tests to confirm they fail (class not found)**

```bash
./gradlew :services:calendar-service:test 2>&1 | head -30
```

Expected: Compilation failure — `CalendarController`, `GoogleCalendarService`, `CalendarServiceApplication` do not exist.

---

## Task 3: Create calendar-service build.gradle and main class

**Files:**
- Create: `services/calendar-service/build.gradle`
- Create: `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarServiceApplication.java`
- Create: `services/calendar-service/src/main/resources/application.yml`

- [ ] **Step 1: Create services/calendar-service/build.gradle**

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation project(':services:shared')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    // Google Calendar API
    implementation platform('com.google.cloud:libraries-bom:26.77.0')
    implementation 'com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0'
    implementation 'com.google.api-client:google-api-client'
    implementation 'com.google.http-client:google-http-client-jackson2'
    implementation 'com.google.auth:google-auth-library-oauth2-http'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('bootBuildImage') {
    imageName = "calendar-service:${version}"
    environment = ['BP_JVM_VERSION': '25']
}
```

- [ ] **Step 2: Create CalendarServiceApplication**

Create `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarServiceApplication.java`:

```java
package com.gm2dev.calendar_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CalendarServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml**

Create `services/calendar-service/src/main/resources/application.yml`:

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
        include: health

logging:
  level:
    com.gm2dev.calendar_service: DEBUG
```

---

## Task 4: Create config properties classes

**Files:**
- Create: `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleOAuthProperties.java`
- Create: `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleCalendarProperties.java`

- [ ] **Step 1: Create GoogleOAuthProperties**

Create `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleOAuthProperties.java`:

```java
package com.gm2dev.calendar_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.google")
public class GoogleOAuthProperties {
    private String clientId;
    private String clientSecret;
}
```

- [ ] **Step 2: Create GoogleCalendarProperties**

Create `services/calendar-service/src/main/java/com/gm2dev/calendar_service/config/GoogleCalendarProperties.java`:

```java
package com.gm2dev.calendar_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.google.calendar")
public class GoogleCalendarProperties {
    private String id = "primary";
    private String refreshToken;
}
```

---

## Task 5: Create GoogleCalendarService in calendar-service

**Files:**
- Create: `services/calendar-service/src/main/java/com/gm2dev/calendar_service/GoogleCalendarService.java`

This is a refactored version of the `GoogleCalendarService` from `core`. The key changes are:
- Takes `CalendarEventRequest` (shared DTO) instead of `Interview` (JPA entity)
- Takes `AttendeeRequest` (shared DTO) for attendee operations
- No `CalendarEventResult` inner record — returns `CalendarEventResponse` (shared DTO) directly
- `buildCalendarClient()` remains package-private for spy-based testing

- [ ] **Step 1: Create GoogleCalendarService**

Create `services/calendar-service/src/main/java/com/gm2dev/calendar_service/GoogleCalendarService.java`:

```java
package com.gm2dev.calendar_service;

import com.gm2dev.calendar_service.config.GoogleCalendarProperties;
import com.gm2dev.calendar_service.config.GoogleOAuthProperties;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class GoogleCalendarService {

    private final GoogleOAuthProperties oAuthProperties;
    private final GoogleCalendarProperties calendarProperties;

    public GoogleCalendarService(GoogleOAuthProperties oAuthProperties,
                                  GoogleCalendarProperties calendarProperties) {
        this.oAuthProperties = oAuthProperties;
        this.calendarProperties = calendarProperties;
    }

    public CalendarEventResponse createEvent(CalendarEventRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(request);

        Event created = calendar.events().insert(calendarId, event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Created Google Calendar event: {}", created.getId());
        return new CalendarEventResponse(created.getId(), created.getHangoutLink());
    }

    public void updateEvent(CalendarEventRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(request);

        calendar.events().update(calendarId, request.googleEventId(), event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Updated Google Calendar event: {}", request.googleEventId());
    }

    public void deleteEvent(String googleEventId) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();

        calendar.events().delete(calendarId, googleEventId)
                .setSendUpdates("all")
                .execute();
        log.debug("Deleted Google Calendar event: {}", googleEventId);
    }

    public void addAttendee(AttendeeRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        String googleEventId = request.googleEventId();
        String attendeeEmail = request.email();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            attendees.addAll(event.getAttendees());
        }
        attendees.add(new EventAttendee().setEmail(attendeeEmail));

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Added attendee {} to event {}", attendeeEmail, googleEventId);
    }

    public void removeAttendee(AttendeeRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        String googleEventId = request.googleEventId();
        String attendeeEmail = request.email();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            event.getAttendees().stream()
                    .filter(a -> !attendeeEmail.equals(a.getEmail()))
                    .forEach(attendees::add);
        }

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Removed attendee {} from event {}", attendeeEmail, googleEventId);
    }

    Calendar buildCalendarClient() throws IOException {
        if (calendarProperties.getRefreshToken() == null || calendarProperties.getRefreshToken().isBlank()) {
            throw new IOException("Google Calendar refresh token not configured");
        }

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(oAuthProperties.getClientId())
                .setClientSecret(oAuthProperties.getClientSecret())
                .setRefreshToken(calendarProperties.getRefreshToken())
                .build();

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Failed to create HTTP transport", e);
        }

        return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Interview Hub - Calendar Service")
                .build();
    }

    private Event buildEvent(CalendarEventRequest request) {
        Event event = new Event();

        String candidateName = request.candidateName() != null ? request.candidateName() : "Unknown";
        event.setSummary(request.techStack() + " Interview - " + candidateName);

        StringBuilder description = new StringBuilder();
        description.append("Tech Stack: ").append(request.techStack());

        description.append("\n\nCandidate Details:");
        description.append("\n  Name: ").append(candidateName);
        if (request.candidateEmail() != null) {
            description.append("\n  Email: ").append(request.candidateEmail());
        }
        if (request.candidateLinkedIn() != null) {
            description.append("\n  LinkedIn: ").append(request.candidateLinkedIn());
        }
        if (request.primaryArea() != null) {
            description.append("\n  Primary Area: ").append(request.primaryArea());
        }
        if (request.feedbackLink() != null) {
            description.append("\n  Feedback Link: ").append(request.feedbackLink());
        }

        event.setDescription(description.toString());

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(request.startTime())));
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(request.endTime())));

        event.setStart(start);
        event.setEnd(end);

        ConferenceSolutionKey solutionKey = new ConferenceSolutionKey().setType("hangoutsMeet");
        CreateConferenceRequest conferenceRequest = new CreateConferenceRequest()
                .setConferenceSolutionKey(solutionKey)
                .setRequestId(UUID.randomUUID().toString());
        event.setConferenceData(new ConferenceData().setCreateRequest(conferenceRequest));

        List<EventAttendee> attendees = new ArrayList<>();

        if (request.interviewerEmail() != null) {
            attendees.add(new EventAttendee().setEmail(request.interviewerEmail()));
        }
        if (request.candidateEmail() != null) {
            attendees.add(new EventAttendee().setEmail(request.candidateEmail()));
        }
        if (request.approvedShadowerEmails() != null) {
            request.approvedShadowerEmails().stream()
                    .filter(email -> email != null)
                    .map(email -> new EventAttendee().setEmail(email))
                    .forEach(attendees::add);
        }

        event.setAttendees(attendees);

        return event;
    }
}
```

---

## Task 6: Create CalendarController

**Files:**
- Create: `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarController.java`

- [ ] **Step 1: Create CalendarController**

Create `services/calendar-service/src/main/java/com/gm2dev/calendar_service/CalendarController.java`:

```java
package com.gm2dev.calendar_service;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final GoogleCalendarService googleCalendarService;

    @PostMapping
    public ResponseEntity<CalendarEventResponse> createEvent(@RequestBody CalendarEventRequest request) throws IOException {
        CalendarEventResponse response = googleCalendarService.createEvent(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<Void> updateEvent(
            @PathVariable String eventId,
            @RequestBody CalendarEventRequest request) throws IOException {
        googleCalendarService.updateEvent(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String eventId) throws IOException {
        googleCalendarService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/attendees")
    public ResponseEntity<Void> addAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        googleCalendarService.addAttendee(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/attendees")
    public ResponseEntity<Void> removeAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        googleCalendarService.removeAttendee(request);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Run calendar-service tests**

```bash
./gradlew :services:calendar-service:test
```

Expected: `CalendarServiceApplicationTest` — 1 test passes. `CalendarControllerTest` — 6 tests pass.

- [ ] **Step 3: Commit calendar-service**

```bash
git add services/calendar-service/ settings.gradle
git commit -m "feat: add calendar-service microservice with REST API"
```

---

## Task 7: Update core — add Feign client, swap GoogleCalendarService

**Files:**
- Modify: `services/core/build.gradle`
- Create: `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java`
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/InterviewHubApplication.java`
- Modify: `services/core/src/main/resources/application.yml`
- Modify: `services/core/src/test/resources/application-test.yml`

- [ ] **Step 1: Update services/core/build.gradle**

Remove the Google Calendar and Cloud Tasks (if not already removed by Plan 2) blocks and add OpenFeign. The relevant diff is:

Remove these lines from `dependencies`:
```groovy
    // Google Cloud BOM
    implementation platform('com.google.cloud:libraries-bom:26.77.0')

    // Google API (Calendar + Auth)
    implementation 'com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0'
    implementation 'com.google.api-client:google-api-client'
    implementation 'com.google.http-client:google-http-client-jackson2'
    implementation 'com.google.auth:google-auth-library-oauth2-http'
```

Add in their place:
```groovy
    // OpenFeign (calls calendar-service)
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
```

Also remove `**/GoogleCalendarService.class` from the JaCoCo exclusions in both `jacocoTestReport` and `jacocoTestCoverageVerification` — the class is being deleted.

- [ ] **Step 2: Create CalendarServiceClient Feign interface**

Create `services/core/src/main/java/com/gm2dev/interview_hub/client/CalendarServiceClient.java`:

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

@FeignClient(name = "calendar-service")
public interface CalendarServiceClient {

    @PostMapping("/events")
    CalendarEventResponse createEvent(@RequestBody CalendarEventRequest request);

    @PutMapping("/events/{eventId}")
    void updateEvent(@PathVariable("eventId") String eventId, @RequestBody CalendarEventRequest request);

    @DeleteMapping("/events/{eventId}")
    void deleteEvent(@PathVariable("eventId") String eventId);

    @PostMapping("/events/{eventId}/attendees")
    void addAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);

    @DeleteMapping("/events/{eventId}/attendees")
    void removeAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);
}
```

- [ ] **Step 3: Add @EnableFeignClients to InterviewHubApplication**

Open `services/core/src/main/java/com/gm2dev/interview_hub/InterviewHubApplication.java` and add the annotation:

```java
package com.gm2dev.interview_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class InterviewHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewHubApplication.class, args);
    }
}
```

- [ ] **Step 4: Remove app.google.calendar from core's application.yml**

In `services/core/src/main/resources/application.yml`, remove the calendar sub-block from `app.google`:

Remove:
```yaml
    calendar:
      id: ${GOOGLE_CALENDAR_ID:primary}
      refresh-token: ${GOOGLE_CALENDAR_REFRESH_TOKEN:}
```

The `app.google` section should now contain only `client-id`, `client-secret`, and `redirect-uri`.

- [ ] **Step 5: Remove app.google.calendar from application-test.yml**

In `services/core/src/test/resources/application-test.yml`, remove:

```yaml
    calendar:
      id: primary
      refresh-token: test-refresh-token
```

---

## Task 8: Update InterviewService to use CalendarServiceClient

**Files:**
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/service/InterviewService.java`

- [ ] **Step 1: Rewrite InterviewService**

Replace `GoogleCalendarService` injection and all usages with `CalendarServiceClient`. Add the `toCalendarRequest()` helper method. The `calendarResult.eventId()` reference changes from `GoogleCalendarService.CalendarEventResult` to `CalendarEventResponse`.

The new `InterviewService.java` — only the changed parts are shown:

Replace the import block and field:
```java
// Remove:
import com.gm2dev.interview_hub.service.GoogleCalendarService;

// Add:
import com.gm2dev.interview_hub.client.CalendarServiceClient;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import java.util.List;
import java.util.Objects;
```

Replace the field declaration:
```java
// Remove:
private final GoogleCalendarService googleCalendarService;

// Add:
private final CalendarServiceClient calendarServiceClient;
```

Replace the `createInterview` calendar block:
```java
        try {
            CalendarEventResponse calendarResult = calendarServiceClient.createEvent(toCalendarRequest(interview));
            interview.setGoogleEventId(calendarResult.eventId());
            interview = interviewRepository.save(interview);
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event for interview {}: {}", interview.getId(), e.getMessage());
        }
```

Replace the `updateInterview` calendar block:
```java
        if (interview.getGoogleEventId() != null) {
            try {
                calendarServiceClient.updateEvent(interview.getGoogleEventId(), toCalendarRequest(interview));
            } catch (Exception e) {
                log.warn("Failed to update Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }
```

Replace the `deleteInterview` calendar block:
```java
        if (interview.getGoogleEventId() != null) {
            try {
                calendarServiceClient.deleteEvent(interview.getGoogleEventId());
            } catch (Exception e) {
                log.warn("Failed to delete Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }
```

Add `toCalendarRequest()` as a private helper (before `buildSummary`):
```java
    private CalendarEventRequest toCalendarRequest(Interview interview) {
        Candidate candidate = interview.getCandidate();
        List<String> shadowerEmails = interview.getShadowingRequests() == null ? List.of() :
                interview.getShadowingRequests().stream()
                        .filter(sr -> ShadowingRequestStatus.APPROVED.equals(sr.getStatus()))
                        .map(sr -> sr.getShadower().getEmail())
                        .filter(Objects::nonNull)
                        .toList();
        return new CalendarEventRequest(
                interview.getGoogleEventId(),
                interview.getTechStack(),
                candidate != null ? candidate.getName() : null,
                candidate != null ? candidate.getEmail() : null,
                candidate != null ? candidate.getLinkedinUrl() : null,
                candidate != null ? candidate.getPrimaryArea() : null,
                candidate != null ? candidate.getFeedbackLink() : null,
                interview.getInterviewer().getEmail(),
                shadowerEmails,
                interview.getStartTime(),
                interview.getEndTime()
        );
    }
```

---

## Task 9: Update ShadowingRequestService to use CalendarServiceClient

**Files:**
- Modify: `services/core/src/main/java/com/gm2dev/interview_hub/service/ShadowingRequestService.java`

- [ ] **Step 1: Rewrite ShadowingRequestService**

Replace `GoogleCalendarService` injection and all usages with `CalendarServiceClient`. Attendee operations now pass `AttendeeRequest` records.

Replace the import and field:
```java
// Remove:
import com.gm2dev.interview_hub.service.GoogleCalendarService;

// Add:
import com.gm2dev.interview_hub.client.CalendarServiceClient;
import com.gm2dev.shared.calendar.AttendeeRequest;
```

Replace the field declaration:
```java
// Remove:
private final GoogleCalendarService googleCalendarService;

// Add:
private final CalendarServiceClient calendarServiceClient;
```

Replace the `cancelShadowingRequest` calendar block:
```java
            if (interview.getGoogleEventId() != null) {
                try {
                    calendarServiceClient.removeAttendee(
                            interview.getGoogleEventId(),
                            new AttendeeRequest(interview.getGoogleEventId(), request.getShadower().getEmail()));
                } catch (Exception e) {
                    log.warn("Failed to remove shadower {} from Calendar event {}: {}",
                            request.getShadower().getEmail(), interview.getGoogleEventId(), e.getMessage());
                }
            }
```

Replace the `approveShadowingRequest` calendar block:
```java
        if (interview.getGoogleEventId() != null) {
            try {
                calendarServiceClient.addAttendee(
                        interview.getGoogleEventId(),
                        new AttendeeRequest(interview.getGoogleEventId(), request.getShadower().getEmail()));
            } catch (Exception e) {
                log.warn("Failed to add shadower {} to Calendar event {}: {}",
                        request.getShadower().getEmail(), interview.getGoogleEventId(),
                        e.getMessage());
            }
        }
```

Replace the `rejectShadowingRequest` calendar block:
```java
            if (interview.getGoogleEventId() != null) {
                try {
                    calendarServiceClient.removeAttendee(
                            interview.getGoogleEventId(),
                            new AttendeeRequest(interview.getGoogleEventId(), request.getShadower().getEmail()));
                } catch (Exception e) {
                    log.warn("Failed to remove shadower {} from Calendar event {}: {}",
                            request.getShadower().getEmail(), interview.getGoogleEventId(), e.getMessage());
                }
            }
```

---

## Task 10: Update core tests — mock CalendarServiceClient instead of GoogleCalendarService

**Files:**
- Modify: `services/core/src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java`
- Modify: `services/core/src/test/java/com/gm2dev/interview_hub/service/ShadowingRequestServiceTest.java`
- Delete: `services/core/src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java`

- [ ] **Step 1: Update InterviewServiceTest**

Replace the `GoogleCalendarService` mock with a `CalendarServiceClient` mock.

In `InterviewServiceTest.java`, replace:
```java
// Remove:
import com.gm2dev.interview_hub.service.GoogleCalendarService;

// Add:
import com.gm2dev.interview_hub.client.CalendarServiceClient;
import com.gm2dev.shared.calendar.CalendarEventResponse;
```

Replace the mock field:
```java
// Remove:
@MockitoBean
private GoogleCalendarService googleCalendarService;

// Add:
@MockitoBean
private CalendarServiceClient calendarServiceClient;
```

Find all tests that stub `googleCalendarService.createEvent(any())` and replace with:
```java
when(calendarServiceClient.createEvent(any()))
    .thenReturn(new CalendarEventResponse("evt-test-id", "https://meet.google.com/test"));
```

Find all tests that stub `googleCalendarService.updateEvent(any())` and remove the stub — `void` methods on mocks are no-ops by default.

Find any `verify(googleCalendarService...)` calls and replace with `verify(calendarServiceClient...)`.

- [ ] **Step 2: Update ShadowingRequestServiceTest**

In `ShadowingRequestServiceTest.java`, make the same swap:

Replace:
```java
// Remove:
import com.gm2dev.interview_hub.service.GoogleCalendarService;

// Add:
import com.gm2dev.interview_hub.client.CalendarServiceClient;
```

Replace the mock field:
```java
// Remove:
@MockitoBean
private GoogleCalendarService googleCalendarService;

// Add:
@MockitoBean
private CalendarServiceClient calendarServiceClient;
```

Update any stubs and verifications from `googleCalendarService.addAttendee(...)` / `removeAttendee(...)` to `calendarServiceClient.addAttendee(...)` / `calendarServiceClient.removeAttendee(...)`.

- [ ] **Step 3: Delete GoogleCalendarServiceTest**

```bash
rm services/core/src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java
```

---

## Task 11: Delete GoogleCalendarService and GoogleCalendarProperties from core

**Files:**
- Delete: `services/core/src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`
- Delete: `services/core/src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java`

- [ ] **Step 1: Delete the files**

```bash
rm services/core/src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java
rm services/core/src/main/java/com/gm2dev/interview_hub/config/GoogleCalendarProperties.java
```

- [ ] **Step 2: Verify no remaining references to GoogleCalendarService in core**

```bash
grep -r "GoogleCalendarService\|GoogleCalendarProperties" services/core/src/
```

Expected: no output. If there are remaining references, fix them before continuing.

- [ ] **Step 3: Run core tests**

```bash
./gradlew :services:core:test
```

Expected: All tests pass. The mocked `CalendarServiceClient` means no real HTTP calls are made.

If tests fail with Feign-related auto-configuration errors in `@SpringBootTest` tests, add to `services/core/src/test/resources/application-test.yml`:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          calendar-service:
            url: http://localhost:9999  # unreachable, but allows context to load
```

- [ ] **Step 4: Commit core changes**

```bash
git add services/core/
git commit -m "feat: replace GoogleCalendarService with CalendarServiceClient Feign client in core"
```

---

## Task 12: Update Docker Compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Add calendar-service to compose.yaml**

Add the `calendar-service` service block and remove `GOOGLE_CALENDAR_REFRESH_TOKEN` and `GOOGLE_CALENDAR_ID` from the `app` service (they now belong only to `calendar-service`):

```yaml
  calendar-service:
    image: calendar-service:0.0.1-SNAPSHOT
    ports:
      - "8082:8082"
    environment:
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
      EUREKA_URL: http://eureka-server:8761/eureka/
    env_file:
      - path: .env
        required: false
    depends_on:
      eureka-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
```

Also update the `app` service `depends_on` to wait for `calendar-service`:

```yaml
  app:
    ...
    depends_on:
      eureka-server:
        condition: service_healthy
      calendar-service:
        condition: service_healthy
```

And remove from the `app` environment block:
```yaml
      GOOGLE_CALENDAR_REFRESH_TOKEN: ${GOOGLE_CALENDAR_REFRESH_TOKEN}
      GOOGLE_CALENDAR_ID: ${GOOGLE_CALENDAR_ID:-primary}
```

- [ ] **Step 2: Build calendar-service image**

```bash
./gradlew :services:calendar-service:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/calendar-service:0.0.1-SNAPSHOT'`

- [ ] **Step 3: Rebuild core image**

```bash
./gradlew :services:core:bootBuildImage
```

Expected: `Successfully built image 'docker.io/library/interview-hub:0.0.1-SNAPSHOT'`

- [ ] **Step 4: Smoke test the full stack**

```bash
docker compose up eureka-server calendar-service app -d
```

Then:
1. Open http://localhost:8761 — Eureka dashboard should show both `CORE` and `CALENDAR-SERVICE` as registered instances.
2. `GET http://localhost:8082/actuator/health` should return `{"status":"UP"}`.
3. Use Postman or curl to create an interview via `POST http://localhost:8080/api/interviews` with a valid Bearer token. Verify the interview is saved (200 OK) and that calendar-service logs show the event creation attempt.

- [ ] **Step 5: Commit**

```bash
git add compose.yaml
git commit -m "chore: add calendar-service to docker compose, remove calendar env vars from core"
```

---

## Self-Review

**Spec coverage:**
- All five REST endpoints implemented (`POST /events`, `PUT /events/{eventId}`, `DELETE /events/{eventId}`, `POST /events/{eventId}/attendees`, `DELETE /events/{eventId}/attendees`)
- `GoogleCalendarService` deleted from core
- `GoogleCalendarProperties` deleted from core
- `GoogleOAuthProperties` stays in core (still needed for Google OAuth login flow)
- Feign client in core calls `calendar-service` by Eureka service name
- `@EnableFeignClients` added to `InterviewHubApplication`
- All calendar failures remain non-blocking (existing `try/catch` catches Feign exceptions)
- `GoogleCalendarServiceTest` deleted from core
- `InterviewServiceTest` and `ShadowingRequestServiceTest` updated to mock `CalendarServiceClient`
- TDD followed: failing tests written before implementation in Task 2
- `application-test.yml` kept in sync with `application.yml` in both core and calendar-service
- `app.google.calendar` config removed from core's yml files

**What this plan does NOT do** (handled in subsequent plans):
- Plan 4: Add Spring Cloud Gateway; migrate nginx API routing to Gateway
