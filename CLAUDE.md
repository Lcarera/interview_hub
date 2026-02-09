# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Interview Hub is a Spring Boot 4.0.2 application for managing technical interviews and shadowing requests. It uses Java 25, PostgreSQL with JPA/Hibernate, and OAuth2 JWT authentication (configured for Supabase).

## Build & Run Commands

**Build the project:**
```bash
./gradlew build
```

**Run the application:**
```bash
./gradlew bootRun
```

**Run tests:**
```bash
./gradlew test
```

**Run a single test class:**
```bash
./gradlew test --tests com.gm2dev.interview_hub.FullyQualifiedTestClassName
```

**Run a single test method:**
```bash
./gradlew test --tests com.gm2dev.interview_hub.FullyQualifiedTestClassName.methodName
```

**Clean build artifacts:**
```bash
./gradlew clean
```

## Architecture

### Package Structure

- `domain/` - JPA entities (Interview, Profile, ShadowingRequest)
- `repository/` - Spring Data JPA repositories
- `service/` - Business logic layer
- `service/dto/` - Data transfer objects
- `config/` - Spring configuration (SecurityConfig)
- `util/` - Utility classes (JsonbConverter)

### Core Domain Model

The application models a three-entity system:

1. **Profile** - Represents users (interviewers and shadowers). The `id` field is externally managed (not auto-generated), suggesting synchronization with an external identity provider (Supabase).

2. **Interview** - Scheduled interviews with:
   - Reference to interviewer (Profile)
   - JSONB field for flexible candidate data storage
   - Google Calendar integration via `googleEventId`
   - Time window (startTime, endTime)
   - Status tracking (SCHEDULED, etc.)
   - One-to-many relationship with ShadowingRequests

3. **ShadowingRequest** - Requests from team members to observe interviews:
   - References to both Interview and shadower (Profile)
   - Status tracking (PENDING, APPROVED, etc.)

### Key Technical Details

**Database:**
- PostgreSQL with JSONB support for semi-structured data
- Hibernate validation mode (`ddl-auto: validate`) - schema changes require external migrations
- Database connection configured via environment variables (DB_URL, DB_USERNAME, DB_PASSWORD)

**Security:**
- OAuth2 Resource Server with JWT validation
- JWT issuer/JWK configured for Supabase (via JWT_ISSUER_URI, JWT_JWK_SET_URI env vars)
- Custom JWT converter extracts "role" claim and prefixes with "ROLE_"
- Stateless sessions (no server-side session management)
- Health endpoint (`/actuator/health`) is public; all other endpoints require authentication

**JSONB Handling:**
- Custom `JsonbConverter` handles Map<String, Object> to PostgreSQL JSONB conversion
- Applied to Interview.candidateInfo field for flexible candidate data

**Logging:**
- DEBUG level enabled for application code and Spring Security
- Services use SLF4j (@Slf4j annotation)

## Environment Variables

Required for runtime:
- `DB_URL` - PostgreSQL JDBC URL (default: jdbc:postgresql://localhost:5432/interview_hub)
- `DB_USERNAME` - Database username (default: postgres)
- `DB_PASSWORD` - Database password (default: postgres)
- `JWT_ISSUER_URI` - OAuth2 JWT issuer URI (Supabase auth endpoint)
- `JWT_JWK_SET_URI` - JWT JWK set URI (Supabase JWKS endpoint)

## Dependencies

Key libraries:
- Spring Boot Web MVC
- Spring Data JPA with PostgreSQL
- Spring Security OAuth2 Resource Server
- Lombok (code generation)
- Jackson (JSON processing for JSONB)
- Google Calendar API v3 (planned integration)
- H2 (test database)

## Database Schema Management

The application uses `hibernate.ddl-auto: validate`, meaning Hibernate will NOT create or modify the schema. Database migrations must be managed externally (likely via Supabase migrations or Flyway/Liquibase if added later).
