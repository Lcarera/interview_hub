# Interview Hub — Backend

Spring Boot 4.0.2 application (Java 25) providing REST APIs for interview management, Google OAuth 2.0 authentication, and Google Calendar integration.

## Package Structure

```
src/main/java/com/gm2dev/interview_hub/
├── InterviewHubApplication.java        # Entry point
├── config/
│   ├── SecurityConfig.java             # OAuth2 Resource Server, JWT, CORS
│   ├── GoogleOAuthProperties.java      # Google OAuth config binding
│   ├── JwtProperties.java              # JWT signing config binding
│   ├── WebConfig.java                  # WebMvc configuration (argument resolvers)
│   └── CurrentUserArgumentResolver.java # Resolves CurrentUser from JWT in SecurityContext
├── controller/
│   ├── AuthController.java             # OAuth login flow, token issuance
│   ├── InterviewController.java        # Interview CRUD
│   ├── ProfileController.java          # Profile retrieval
│   ├── ShadowingRequestController.java # Shadowing request management
│   └── GlobalExceptionHandler.java     # Centralized error handling
├── domain/
│   ├── Profile.java                    # User entity
│   ├── Interview.java                  # Interview entity (JSONB candidateInfo)
│   ├── ShadowingRequest.java           # Shadowing request entity
│   ├── InterviewStatus.java            # SCHEDULED | COMPLETED | CANCELLED
│   └── ShadowingRequestStatus.java     # PENDING | APPROVED | REJECTED | CANCELLED
├── dto/
│   ├── AuthResponse.java              # {token, expiresIn, email}
│   ├── CreateInterviewRequest.java    # Interview creation payload
│   ├── UpdateInterviewRequest.java    # Interview update payload
│   ├── InterviewDto.java              # Interview response DTO
│   ├── InterviewSummaryDto.java       # Lightweight interview summary
│   ├── ProfileDto.java                # Profile response DTO
│   ├── ShadowingRequestDto.java       # Shadowing request response DTO
│   ├── ShadowingRequestSummaryDto.java # Lightweight shadowing summary
│   ├── RejectShadowingRequest.java    # {reason} payload
│   └── CurrentUser.java               # Authenticated user ID (resolved by CurrentUserArgumentResolver)
├── mapper/
│   ├── InterviewMapper.java           # MapStruct: Interview <-> DTOs
│   ├── ProfileMapper.java            # MapStruct: Profile <-> DTOs
│   └── ShadowingRequestMapper.java   # MapStruct: ShadowingRequest <-> DTOs
├── repository/
│   ├── InterviewRepository.java       # Spring Data JPA
│   ├── ProfileRepository.java         # Spring Data JPA
│   └── ShadowingRequestRepository.java # Spring Data JPA
├── service/
│   ├── AuthService.java               # OAuth flow, delegates JWT issuance to JwtService
│   ├── EmailPasswordAuthService.java  # Email/password auth, delegates JWT issuance to JwtService
│   ├── JwtService.java                # Interface for JWT token issuance
│   ├── HmacJwtService.java            # HMAC-SHA256 JWT implementation
│   ├── InterviewService.java          # Interview business logic
│   ├── ShadowingRequestService.java   # Shadowing request business logic
│   └── ProfileService.java            # Profile business logic
├── client/
│   └── CalendarServiceClient.java     # OpenFeign client for calendar-service (Eureka discovery)
```

## Domain Model

```
┌──────────┐       ┌────────────┐       ┌────────────────────┐
│ Profile   │ 1───* │ Interview   │ 1───* │ ShadowingRequest    │
│           │       │             │       │                     │
│ id (UUID) │       │ id (UUID)   │       │ id (UUID)           │
│ email     │       │ interviewer │◄──────│ interview           │
│ role      │       │ candidate   │       │ shadower ───────────┤►Profile
│ googleSub │       │ start/end   │       │ status              │
└──────────┘       │ status      │       │ reason              │
                   │ googleEventId│       └────────────────────┘
    ┌──────────┐   └─────┬───────┘
    │ Candidate │ *───1   │
    │ name/email│◄────────┘
    │ linkedin  │
    └──────────┘
```

- **Profile** — Users (interviewers and shadowers). UUID `id` is app-generated on first OAuth login.
- **Candidate** — External candidates being interviewed. Name, email (required), LinkedIn URL, primary area/tech.
- **Interview** — Scheduled interviews linking an interviewer, candidate, and optional talent acquisition contact. Linked to a Google Calendar event via `googleEventId`.
- **ShadowingRequest** — Requests to observe an interview. Status transitions: PENDING → APPROVED/REJECTED/CANCELLED.

## REST API

### Authentication (public — no Bearer token required)

| Method | Path                    | Description                                      |
|--------|-------------------------|--------------------------------------------------|
| GET    | `/auth/google`          | Redirects to Google OAuth consent screen          |
| GET    | `/auth/google/callback` | OAuth callback — redirects to frontend with token |
| POST   | `/auth/token`           | Token exchange (Postman-compatible, form params)  |

### Interviews (Bearer token required)

| Method | Path                  | Description                              |
|--------|-----------------------|------------------------------------------|
| POST   | `/api/interviews`     | Create interview + Google Calendar event |
| GET    | `/api/interviews`     | List interviews (paginated: `page`, `size`, `sort`) |
| GET    | `/api/interviews/{id}`| Get interview by ID                      |
| PUT    | `/api/interviews/{id}`| Update interview (interviewer only)      |
| DELETE | `/api/interviews/{id}`| Delete interview (interviewer only)      |

### Profiles (Bearer token required)

| Method | Path               | Description                          |
|--------|--------------------|--------------------------------------|
| GET    | `/api/profiles/me` | Get current user's profile           |
| GET    | `/api/profiles`    | List all profiles                    |

### Shadowing Requests (Bearer token required)

| Method | Path                                                  | Description                           |
|--------|-------------------------------------------------------|---------------------------------------|
| GET    | `/api/interviews/{interviewId}/shadowing-requests`   | List shadowing requests for interview |
| GET    | `/api/shadowing-requests/my`                          | List current user's requests          |
| POST   | `/api/interviews/{interviewId}/shadowing-requests`   | Request to shadow an interview        |
| POST   | `/api/shadowing-requests/{id}/approve`                | Approve request (interviewer only)    |
| POST   | `/api/shadowing-requests/{id}/reject`                 | Reject request with reason            |
| POST   | `/api/shadowing-requests/{id}/cancel`                 | Cancel own request (shadower only)    |

### Health (public)

| Method | Path                | Description  |
|--------|---------------------|--------------|
| GET    | `/actuator/health`  | Health check |

## Authentication Flow

1. Frontend redirects user to `GET /auth/google`
2. Backend redirects to Google OAuth consent (scopes: openid, email, profile)
3. Google redirects back to `GET /auth/google/callback` with an authorization code
4. Backend exchanges code for Google tokens, validates domain allowlist, creates/updates Profile
5. Backend issues an HMAC-SHA256 JWT (1-hour expiry) via `JwtService` and redirects to the frontend with the token in the URL hash fragment
6. Frontend stores the token in localStorage and attaches it as `Authorization: Bearer <token>` on all API calls

Only `@gm2dev.com` and `@lcarera.dev` accounts are allowed (configured in `AllowedDomains.ALLOWED_DOMAINS`).

**JWT Issuance Architecture:**
- `JwtService` interface centralizes token creation logic
- `HmacJwtService` implements JWT issuance using HMAC-SHA256
- Both `AuthService` (OAuth) and `EmailPasswordAuthService` (email/password) delegate to `JwtService`

**CurrentUser Resolution:**
- Controllers use `CurrentUser currentUser` parameter instead of `@AuthenticationPrincipal Jwt jwt`
- `CurrentUserArgumentResolver` automatically extracts the user ID from the JWT in the SecurityContext
- Eliminates repetitive `UUID.fromString(jwt.getSubject())` boilerplate

## Google Calendar Integration

| Action                        | Calendar Effect                                    |
|-------------------------------|----------------------------------------------------|
| Create interview              | Creates event via calendar-service, adds interviewer + candidate as attendees |
| Update interview              | Updates the Calendar event via calendar-service    |
| Delete interview              | Cancels the Calendar event via calendar-service    |
| Approve shadowing request     | Adds shadower as attendee via calendar-service     |

Calendar operations are delegated to the `calendar-service` microservice via OpenFeign (`CalendarServiceClient`). Core discovers calendar-service through Eureka. Calendar API failures are logged but do **not** block the primary database operation.

## Configuration

Key properties from `application.yml`:

| Property                          | Env Variable           | Description                         |
|-----------------------------------|------------------------|-------------------------------------|
| `spring.datasource.url`          | `DB_URL`               | PostgreSQL JDBC connection string   |
| `spring.datasource.username`     | `DB_USERNAME`          | Database username                   |
| `spring.datasource.password`     | `DB_PASSWORD`          | Database password                   |
| `app.google.client-id`           | `GOOGLE_CLIENT_ID`     | Google OAuth client ID              |
| `app.google.client-secret`       | `GOOGLE_CLIENT_SECRET` | Google OAuth client secret          |
| `app.jwt.signing-secret`         | `JWT_SIGNING_SECRET`   | HMAC-SHA256 signing key (min 32 bytes) |
| `app.jwt.expiration-seconds`     | -                      | JWT expiry (default: 3600)          |
| `app.frontend-url`               | `FRONTEND_URL`         | Frontend URL for OAuth redirects    |
| `app.google.redirect-uri`        | `APP_BASE_URL`         | Backend URL + `/auth/google/callback` |
| _(calendar config moved to calendar-service)_ | | |

Hibernate uses `ddl-auto: validate` — it will not modify the schema.

## Build & Run

```bash
# Build
./gradlew build

# Build Docker image
./gradlew bootBuildImage

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests com.gm2dev.interview_hub.FullyQualifiedTestClassName

# Run a single test method
./gradlew test --tests com.gm2dev.interview_hub.ClassName.methodName

# Clean
./gradlew clean
```

> **Note:** Do not use `./gradlew bootRun` — the `spring-boot-docker-compose` devtools will attempt to manage Docker and fail if Docker Desktop's Linux engine isn't available. Use `docker compose up` instead.

## Testing

Two test styles are used — never mix them:

### Controller Tests (`@WebMvcTest`)

- Annotated with `@WebMvcTest(XController.class)` + `@Import(SecurityConfig.class)` + `@ActiveProfiles("test")`
- Mock the service layer with `@MockitoBean`
- Authenticate requests with `.with(jwt())` from spring-security-test
- Test HTTP status codes, response bodies, and error handling

### Service Tests (`@SpringBootTest`)

- `@SpringBootTest` + `@ActiveProfiles("test")` + `@Transactional` + `@Rollback`
- Full Spring context with H2 in-memory database (`ddl-auto: create-drop`)
- `CalendarServiceClient` (Feign interface) must be `@MockitoBean`'d in ALL `@SpringBootTest` classes (not just service tests — `CandidateServiceTest` needs it too)
- `AuthServiceTest`, `HmacJwtServiceTest`, and `CurrentUserArgumentResolverTest` use `@ExtendWith(MockitoExtension.class)` without Spring context
- `AuthService` and `EmailPasswordAuthService` tests mock `JwtService` instead of `JwtEncoder`/`JwtProperties`

### Code Coverage

JaCoCo enforces **95% branch coverage**. Excluded classes:
- `InterviewHubApplication`
- `OpenApiConfig`
- `SecurityConfig`
- `*MapperImpl`

## Dependencies

| Dependency                                | Purpose                              |
|-------------------------------------------|--------------------------------------|
| Spring Boot 4.0.2 Web MVC                 | REST API framework                   |
| Spring Data JPA + PostgreSQL              | Database access                      |
| Spring Security OAuth2 Resource Server    | JWT validation                       |
| Spring Security OAuth2 Client             | Google OAuth flow                    |
| Spring Boot Actuator                      | Health endpoints                     |
| Google API Client                         | OAuth token exchange (AuthService)   |
| Spring Cloud OpenFeign                    | Declarative HTTP client (calendar-service) |
| MapStruct 1.6.3                           | Entity ↔ DTO mapping                |
| Lombok                                    | Boilerplate reduction                |
| Jackson                                   | JSON/JSONB processing                |
| H2                                        | In-memory test database              |
