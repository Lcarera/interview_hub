# Candidate Domain Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract candidate data from the JSONB `candidateInfo` field into a proper `Candidate` entity with structured fields, and add a talent acquisition (TA) Profile relation to Interview.

**Architecture:** Create a new `Candidate` JPA entity with its own table, repository, service, controller, DTOs, and mapper. Modify `Interview` to replace `candidateInfo` (JSONB Map) with a `@ManyToOne` to `Candidate`, and add a nullable `@ManyToOne` to `Profile` for the talent acquisition relation. Update `GoogleCalendarService` to read candidate data from the entity instead of the JSONB map.

**Interview–Candidate UX pattern (either/or):** When creating or updating an interview, the caller provides **either** `candidateId` (reference an existing candidate) **or** inline `candidate` data (create a new one). Exactly one must be non-null — the backend validates this. This avoids forcing a two-step "create candidate then create interview" flow while still allowing reuse of existing candidates.

**Tech Stack:** Spring Boot 4.0.2, Java 25, JPA/Hibernate, H2 (tests), PostgreSQL (prod), MapStruct, Lombok, JUnit 5, Mockito

---

### Task 1: Database Migration

**Files:**
- Create: `supabase/migrations/004_create_candidates_table.sql`

**Step 1: Write the migration SQL**

```sql
-- Create candidates table
CREATE TABLE public.candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    linkedin_url VARCHAR(500),
    primary_area VARCHAR(255),
    feedback_link VARCHAR(500)
);

CREATE INDEX idx_candidates_email ON public.candidates(email);

-- Add candidate_id and talent_acquisition_id to interviews
ALTER TABLE public.interviews ADD COLUMN candidate_id UUID REFERENCES public.candidates(id);
ALTER TABLE public.interviews ADD COLUMN talent_acquisition_id UUID REFERENCES public.profiles(id);

-- Drop the old JSONB column
ALTER TABLE public.interviews DROP COLUMN candidate_info;

CREATE INDEX idx_interviews_candidate_id ON public.interviews(candidate_id);
CREATE INDEX idx_interviews_talent_acquisition_id ON public.interviews(talent_acquisition_id);
```

**Step 2: Commit**

```bash
git add supabase/migrations/004_create_candidates_table.sql
git commit -m "feat: add candidates table and interview FK migration"
```

---

### Task 2: Candidate Entity

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/domain/Candidate.java`

**Step 1: Write the entity**

```java
package com.gm2dev.interview_hub.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "candidates", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "primary_area")
    private String primaryArea;

    @Column(name = "feedback_link")
    private String feedbackLink;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/domain/Candidate.java
git commit -m "feat: add Candidate entity"
```

---

### Task 3: Modify Interview Entity

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/domain/Interview.java`

**Step 1: Replace candidateInfo with candidate and talentAcquisition relations**

Remove these lines:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private Map<String, Object> candidateInfo;
```

Remove the `Map` and `SqlTypes` imports if no longer used:
```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
```

Add these fields (after the `interviewer` field):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "candidate_id")
private Candidate candidate;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "talent_acquisition_id")
private Profile talentAcquisition;
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/domain/Interview.java
git commit -m "feat: replace candidateInfo JSONB with Candidate and TA relations"
```

---

### Task 4: Candidate Repository

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/repository/CandidateRepository.java`

**Step 1: Write the repository**

```java
package com.gm2dev.interview_hub.repository;

import com.gm2dev.interview_hub.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    Optional<Candidate> findByEmail(String email);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/repository/CandidateRepository.java
git commit -m "feat: add CandidateRepository"
```

---

### Task 5: Candidate DTOs

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/service/dto/CandidateDto.java`
- Create: `src/main/java/com/gm2dev/interview_hub/service/dto/CreateCandidateRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/service/dto/UpdateCandidateRequest.java`

**Step 1: Write CandidateDto**

```java
package com.gm2dev.interview_hub.service.dto;

import lombok.Value;

import java.util.UUID;

@Value
public class CandidateDto {
    UUID id;
    String name;
    String email;
    String linkedinUrl;
    String primaryArea;
    String feedbackLink;
}
```

**Step 2: Write CreateCandidateRequest**

```java
package com.gm2dev.interview_hub.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCandidateRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String linkedinUrl;

    private String primaryArea;

    private String feedbackLink;
}
```

**Step 3: Write UpdateCandidateRequest**

```java
package com.gm2dev.interview_hub.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCandidateRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String linkedinUrl;

    private String primaryArea;

    private String feedbackLink;
}
```

**Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/dto/CandidateDto.java \
        src/main/java/com/gm2dev/interview_hub/service/dto/CreateCandidateRequest.java \
        src/main/java/com/gm2dev/interview_hub/service/dto/UpdateCandidateRequest.java
git commit -m "feat: add Candidate DTOs"
```

---

### Task 6: Candidate Mapper

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/service/mapper/CandidateMapper.java`

**Step 1: Write the mapper**

```java
package com.gm2dev.interview_hub.service.mapper;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.service.dto.CandidateDto;
import com.gm2dev.interview_hub.service.dto.UpdateCandidateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CandidateMapper {
    CandidateDto toDto(Candidate candidate);

    void updateFromRequest(UpdateCandidateRequest request, @MappingTarget Candidate candidate);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/mapper/CandidateMapper.java
git commit -m "feat: add CandidateMapper"
```

---

### Task 7: Candidate Service — Tests First

**Files:**
- Create: `src/test/java/com/gm2dev/interview_hub/service/CandidateServiceTest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/service/CandidateService.java`

**Step 1: Write the failing test**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.service.dto.CreateCandidateRequest;
import com.gm2dev.interview_hub.service.dto.UpdateCandidateRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class CandidateServiceTest {

    @Autowired
    private CandidateService candidateService;

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    @Test
    void createCandidate_withValidRequest_returnsCandidate() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                "Jane Doe", "jane@example.com", "https://linkedin.com/in/janedoe", "Java", "https://feedback.link/123"
        );

        Candidate candidate = candidateService.createCandidate(request);

        assertNotNull(candidate.getId());
        assertEquals("Jane Doe", candidate.getName());
        assertEquals("jane@example.com", candidate.getEmail());
        assertEquals("https://linkedin.com/in/janedoe", candidate.getLinkedinUrl());
        assertEquals("Java", candidate.getPrimaryArea());
        assertEquals("https://feedback.link/123", candidate.getFeedbackLink());
    }

    @Test
    void findById_withExistingId_returnsCandidate() {
        CreateCandidateRequest request = new CreateCandidateRequest(
                "Jane Doe", "jane@example.com", null, null, null
        );
        Candidate created = candidateService.createCandidate(request);

        Candidate found = candidateService.findById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("Jane Doe", found.getName());
    }

    @Test
    void findById_withNonExistentId_throwsEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(EntityNotFoundException.class, () -> candidateService.findById(nonExistentId));
    }

    @Test
    void findAll_returnsAllCandidates() {
        candidateService.createCandidate(new CreateCandidateRequest("A", "a@test.com", null, null, null));
        candidateService.createCandidate(new CreateCandidateRequest("B", "b@test.com", null, null, null));

        var all = candidateService.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void updateCandidate_withValidRequest_updatesFields() {
        Candidate created = candidateService.createCandidate(
                new CreateCandidateRequest("Jane Doe", "jane@example.com", null, "Java", null)
        );

        UpdateCandidateRequest update = new UpdateCandidateRequest(
                "Jane Smith", "jane.smith@example.com", "https://linkedin.com/in/janesmith", "React", "https://feedback.link/456"
        );
        Candidate updated = candidateService.updateCandidate(created.getId(), update);

        assertEquals("Jane Smith", updated.getName());
        assertEquals("jane.smith@example.com", updated.getEmail());
        assertEquals("https://linkedin.com/in/janesmith", updated.getLinkedinUrl());
        assertEquals("React", updated.getPrimaryArea());
        assertEquals("https://feedback.link/456", updated.getFeedbackLink());
    }

    @Test
    void deleteCandidate_withExistingId_deletesCandidate() {
        Candidate created = candidateService.createCandidate(
                new CreateCandidateRequest("Jane Doe", "jane@example.com", null, null, null)
        );

        candidateService.deleteCandidate(created.getId());

        assertThrows(EntityNotFoundException.class, () -> candidateService.findById(created.getId()));
    }

    @Test
    void deleteCandidate_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class, () -> candidateService.deleteCandidate(UUID.randomUUID()));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.CandidateServiceTest`
Expected: Compilation failure — `CandidateService` does not exist

**Step 3: Write the service implementation**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.repository.CandidateRepository;
import com.gm2dev.interview_hub.service.dto.CreateCandidateRequest;
import com.gm2dev.interview_hub.service.dto.UpdateCandidateRequest;
import com.gm2dev.interview_hub.service.mapper.CandidateMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;

    @Transactional
    public Candidate createCandidate(CreateCandidateRequest request) {
        Candidate candidate = new Candidate();
        candidate.setName(request.getName());
        candidate.setEmail(request.getEmail());
        candidate.setLinkedinUrl(request.getLinkedinUrl());
        candidate.setPrimaryArea(request.getPrimaryArea());
        candidate.setFeedbackLink(request.getFeedbackLink());
        return candidateRepository.save(candidate);
    }

    @Transactional(readOnly = true)
    public Candidate findById(UUID id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Candidate> findAll() {
        return candidateRepository.findAll();
    }

    @Transactional
    public Candidate updateCandidate(UUID id, UpdateCandidateRequest request) {
        Candidate candidate = findById(id);
        candidateMapper.updateFromRequest(request, candidate);
        return candidateRepository.save(candidate);
    }

    @Transactional
    public void deleteCandidate(UUID id) {
        Candidate candidate = findById(id);
        candidateRepository.delete(candidate);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.CandidateServiceTest`
Expected: All 7 tests PASS

**Step 5: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/CandidateServiceTest.java \
        src/main/java/com/gm2dev/interview_hub/service/CandidateService.java
git commit -m "feat: add CandidateService with tests"
```

---

### Task 8: Candidate Controller — Tests First

**Files:**
- Create: `src/test/java/com/gm2dev/interview_hub/controller/CandidateControllerTest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/controller/CandidateController.java`

**Step 1: Write the failing controller test**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.service.CandidateService;
import com.gm2dev.interview_hub.service.dto.CandidateDto;
import com.gm2dev.interview_hub.service.dto.CreateCandidateRequest;
import com.gm2dev.interview_hub.service.dto.UpdateCandidateRequest;
import com.gm2dev.interview_hub.service.mapper.CandidateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class CandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidateService candidateService;

    @MockitoBean
    private CandidateMapper candidateMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createCandidate_returns201() throws Exception {
        CreateCandidateRequest request = new CreateCandidateRequest(
                "Jane Doe", "jane@example.com", "https://linkedin.com/in/janedoe", "Java", "https://feedback.link/123"
        );
        Candidate candidate = buildCandidate();
        CandidateDto dto = buildCandidateDto(candidate.getId());

        when(candidateService.createCandidate(any(CreateCandidateRequest.class))).thenReturn(candidate);
        when(candidateMapper.toDto(candidate)).thenReturn(dto);

        mockMvc.perform(post("/api/candidates")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void listCandidates_returns200() throws Exception {
        Candidate candidate = buildCandidate();
        CandidateDto dto = buildCandidateDto(candidate.getId());

        when(candidateService.findAll()).thenReturn(List.of(candidate));
        when(candidateMapper.toDto(candidate)).thenReturn(dto);

        mockMvc.perform(get("/api/candidates")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Jane Doe"));
    }

    @Test
    void getCandidate_returns200() throws Exception {
        Candidate candidate = buildCandidate();
        CandidateDto dto = buildCandidateDto(candidate.getId());

        when(candidateService.findById(candidate.getId())).thenReturn(candidate);
        when(candidateMapper.toDto(candidate)).thenReturn(dto);

        mockMvc.perform(get("/api/candidates/{id}", candidate.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void updateCandidate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateCandidateRequest request = new UpdateCandidateRequest(
                "Jane Smith", "jane.smith@example.com", null, "React", null
        );
        Candidate updated = buildCandidate();
        updated.setId(id);
        updated.setName("Jane Smith");
        CandidateDto dto = new CandidateDto(id, "Jane Smith", "jane.smith@example.com", null, "React", null);

        when(candidateService.updateCandidate(eq(id), any(UpdateCandidateRequest.class))).thenReturn(updated);
        when(candidateMapper.toDto(updated)).thenReturn(dto);

        mockMvc.perform(put("/api/candidates/{id}", id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Smith"));
    }

    @Test
    void deleteCandidate_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/candidates/{id}", id)
                        .with(jwt()))
                .andExpect(status().isNoContent());

        verify(candidateService).deleteCandidate(id);
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/candidates"))
                .andExpect(status().isUnauthorized());
    }

    private Candidate buildCandidate() {
        Candidate c = new Candidate();
        c.setId(UUID.randomUUID());
        c.setName("Jane Doe");
        c.setEmail("jane@example.com");
        c.setLinkedinUrl("https://linkedin.com/in/janedoe");
        c.setPrimaryArea("Java");
        c.setFeedbackLink("https://feedback.link/123");
        return c;
    }

    private CandidateDto buildCandidateDto(UUID id) {
        return new CandidateDto(id, "Jane Doe", "jane@example.com",
                "https://linkedin.com/in/janedoe", "Java", "https://feedback.link/123");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.controller.CandidateControllerTest`
Expected: Compilation failure — `CandidateController` does not exist

**Step 3: Write the controller**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.service.CandidateService;
import com.gm2dev.interview_hub.service.dto.CandidateDto;
import com.gm2dev.interview_hub.service.dto.CreateCandidateRequest;
import com.gm2dev.interview_hub.service.dto.UpdateCandidateRequest;
import com.gm2dev.interview_hub.service.mapper.CandidateMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto createCandidate(@Valid @RequestBody CreateCandidateRequest request) {
        return candidateMapper.toDto(candidateService.createCandidate(request));
    }

    @GetMapping
    public List<CandidateDto> listCandidates() {
        return candidateService.findAll().stream()
                .map(candidateMapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public CandidateDto getCandidate(@PathVariable UUID id) {
        return candidateMapper.toDto(candidateService.findById(id));
    }

    @PutMapping("/{id}")
    public CandidateDto updateCandidate(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateCandidateRequest request) {
        return candidateMapper.toDto(candidateService.updateCandidate(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable UUID id) {
        candidateService.deleteCandidate(id);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.gm2dev.interview_hub.controller.CandidateControllerTest`
Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/controller/CandidateControllerTest.java \
        src/main/java/com/gm2dev/interview_hub/controller/CandidateController.java
git commit -m "feat: add CandidateController with tests"
```

---

### Task 9: Update Interview DTOs

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/dto/CreateInterviewRequest.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/service/dto/UpdateInterviewRequest.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/service/dto/InterviewDto.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/service/dto/InterviewSummaryDto.java`

**Step 1: Update CreateInterviewRequest**

Replace `candidateInfo` field with the either/or candidate pattern:
```java
private UUID candidateId;

private CreateCandidateRequest candidate;

private UUID talentAcquisitionId;
```

No `@NotNull` on either candidate field — validation is custom (exactly one must be non-null). Remove the `Map` import, add `import com.gm2dev.interview_hub.service.dto.CreateCandidateRequest;`.

Full field list after changes:
- `interviewerId` (UUID, @NotNull)
- `candidateId` (UUID, nullable — mutually exclusive with `candidate`)
- `candidate` (CreateCandidateRequest, nullable — mutually exclusive with `candidateId`)
- `talentAcquisitionId` (UUID, nullable)
- `techStack` (String, @NotBlank)
- `startTime` (Instant, @NotNull @Future)
- `endTime` (Instant, @NotNull @Future)

Add a custom validation method:
```java
@AssertTrue(message = "Exactly one of candidateId or candidate must be provided")
@JsonIgnore
public boolean isCandidateValid() {
    return (candidateId != null) ^ (candidate != null);
}
```

Add imports: `jakarta.validation.constraints.AssertTrue`, `com.fasterxml.jackson.annotation.JsonIgnore`.

**Step 2: Update UpdateInterviewRequest**

Same either/or pattern for candidate:
```java
private UUID candidateId;

private CreateCandidateRequest candidate;

private UUID talentAcquisitionId;
```

Full field list after changes:
- `candidateId` (UUID, nullable — mutually exclusive with `candidate`)
- `candidate` (CreateCandidateRequest, nullable — mutually exclusive with `candidateId`)
- `talentAcquisitionId` (UUID, nullable)
- `techStack` (String, @NotBlank)
- `startTime` (Instant, @NotNull @Future)
- `endTime` (Instant, @NotNull @Future)
- `status` (InterviewStatus, @NotNull)

Same `@AssertTrue isCandidateValid()` method as CreateInterviewRequest.

**Step 3: Update InterviewDto**

Replace `candidateInfo` field with:
```java
CandidateDto candidate;
ProfileDto talentAcquisition;
```

Remove the `Map` import. Full field list after changes:
- `id` (UUID)
- `interviewer` (ProfileDto)
- `candidate` (CandidateDto)
- `talentAcquisition` (ProfileDto)
- `techStack` (String)
- `startTime` (Instant)
- `endTime` (Instant)
- `status` (InterviewStatus)
- `shadowingRequests` (List<ShadowingRequestSummaryDto>)

**Step 4: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/dto/CreateInterviewRequest.java \
        src/main/java/com/gm2dev/interview_hub/service/dto/UpdateInterviewRequest.java \
        src/main/java/com/gm2dev/interview_hub/service/dto/InterviewDto.java
git commit -m "feat: update Interview DTOs for Candidate relation"
```

---

### Task 10: Update InterviewMapper

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/mapper/InterviewMapper.java`

**Step 1: Add CandidateMapper to uses**

The mapper needs to know how to map `Candidate` → `CandidateDto`. Update the `@Mapper` annotation:

```java
@Mapper(componentModel = "spring", uses = {ProfileMapper.class, ShadowingRequestMapper.class, CandidateMapper.class})
public interface InterviewMapper {
    InterviewDto toDto(Interview interview);
    InterviewSummaryDto toSummaryDto(Interview interview);

    @Mapping(target = "candidate", ignore = true)
    @Mapping(target = "talentAcquisition", ignore = true)
    @Mapping(target = "interviewer", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "googleEventId", ignore = true)
    @Mapping(target = "shadowingRequests", ignore = true)
    void updateFromRequest(UpdateInterviewRequest request, @MappingTarget Interview interview);
}
```

Note: `candidate` and `talentAcquisition` are resolved by ID in the service layer, not mapped directly from the DTO. The `updateFromRequest` should only map simple fields (techStack, startTime, endTime, status). Add `@Mapping(target = ..., ignore = true)` for relation fields that the service sets manually.

**Step 2: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/mapper/InterviewMapper.java
git commit -m "feat: update InterviewMapper for Candidate relation"
```

---

### Task 11: Update InterviewService

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/InterviewService.java`

**Step 1: Add CandidateRepository dependency**

Add to the class fields:
```java
private final CandidateRepository candidateRepository;
```

**Step 2: Add a private helper to resolve the either/or candidate**

```java
private Candidate resolveCandidate(UUID candidateId, CreateCandidateRequest candidateRequest) {
    if (candidateId != null) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + candidateId));
    }
    return candidateService.createCandidate(candidateRequest);
}
```

Add `CandidateService` as a dependency (constructor injection via `@RequiredArgsConstructor`).

**Step 3: Update createInterview**

Replace the `candidateInfo` line with the either/or candidate resolution and TA lookups:
```java
@Transactional
public Interview createInterview(CreateInterviewRequest request) {
    Profile interviewer = profileRepository.findById(request.getInterviewerId())
            .orElseThrow(() -> new EntityNotFoundException("Interviewer not found"));

    Candidate candidate = resolveCandidate(request.getCandidateId(), request.getCandidate());

    Interview interview = new Interview();
    interview.setInterviewer(interviewer);
    interview.setCandidate(candidate);
    interview.setTechStack(request.getTechStack());
    interview.setStartTime(request.getStartTime());
    interview.setEndTime(request.getEndTime());
    interview.setStatus(InterviewStatus.SCHEDULED);

    if (request.getTalentAcquisitionId() != null) {
        Profile ta = profileRepository.findById(request.getTalentAcquisitionId())
                .orElseThrow(() -> new EntityNotFoundException("Talent acquisition profile not found"));
        interview.setTalentAcquisition(ta);
    }

    interview = interviewRepository.save(interview);

    try {
        String googleEventId = googleCalendarService.createEvent(interviewer, interview);
        interview.setGoogleEventId(googleEventId);
        interview = interviewRepository.save(interview);
    } catch (Exception e) {
        log.warn("Failed to create Google Calendar event for interview {}: {}",
                 interview.getId(), e.getMessage());
    }

    return interview;
}
```

**Step 4: Update updateInterview**

After `interviewMapper.updateFromRequest(request, interview)`, add either/or candidate resolution and TA resolution:
```java
Candidate candidate = resolveCandidate(request.getCandidateId(), request.getCandidate());
interview.setCandidate(candidate);

if (request.getTalentAcquisitionId() != null) {
    Profile ta = profileRepository.findById(request.getTalentAcquisitionId())
            .orElseThrow(() -> new EntityNotFoundException("Talent acquisition profile not found"));
    interview.setTalentAcquisition(ta);
} else {
    interview.setTalentAcquisition(null);
}
```

**Step 5: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/InterviewService.java
git commit -m "feat: update InterviewService for Candidate and TA relations"
```

---

### Task 12: Update GoogleCalendarService

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`

**Step 1: Update buildEvent to use Candidate entity**

Replace the `extractCandidateName` and `extractCandidateEmail` calls. The method should read from `interview.getCandidate()`:

```java
private Event buildEvent(Profile interviewer, Interview interview) {
    Event event = new Event();

    Candidate candidate = interview.getCandidate();
    String candidateName = candidate != null ? candidate.getName() : "Unknown";
    event.setSummary(interview.getTechStack() + " Interview - " + candidateName);

    StringBuilder description = new StringBuilder();
    description.append("Tech Stack: ").append(interview.getTechStack());

    if (candidate != null) {
        description.append("\n\nCandidate Details:");
        description.append("\n  Name: ").append(candidate.getName());
        description.append("\n  Email: ").append(candidate.getEmail());
        if (candidate.getLinkedinUrl() != null) {
            description.append("\n  LinkedIn: ").append(candidate.getLinkedinUrl());
        }
        if (candidate.getPrimaryArea() != null) {
            description.append("\n  Area: ").append(candidate.getPrimaryArea());
        }
        if (candidate.getFeedbackLink() != null) {
            description.append("\n  Feedback: ").append(candidate.getFeedbackLink());
        }
    }
    event.setDescription(description.toString());

    // ... rest of method (times, conference, attendees) stays the same ...

    // Update attendee extraction to use candidate entity
    List<EventAttendee> attendees = new ArrayList<>();
    attendees.add(new EventAttendee().setEmail(interviewer.getEmail()));

    if (candidate != null && candidate.getEmail() != null) {
        attendees.add(new EventAttendee().setEmail(candidate.getEmail()));
    }
    event.setAttendees(attendees);

    return event;
}
```

**Step 2: Remove the now-unused helper methods**

Delete `extractCandidateName(Map<String, Object>)` and `extractCandidateEmail(Map<String, Object>)`.

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java
git commit -m "feat: update GoogleCalendarService to use Candidate entity"
```

---

### Task 13: Update Existing Tests

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java`
- Modify: `src/test/java/com/gm2dev/interview_hub/controller/InterviewControllerTest.java`
- Modify: `src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java`

**Step 1: Update InterviewServiceTest**

All test helper methods that build `Interview` objects need updating:
- Replace `interview.setCandidateInfo(Map.of("name", "...", "email", "..."))` with:
  ```java
  Candidate candidate = new Candidate();
  candidate.setName("Test Candidate");
  candidate.setEmail("candidate@example.com");
  candidate = candidateRepository.save(candidate);
  ```
- Add `@Autowired CandidateRepository candidateRepository;`
- Update `CreateInterviewRequest` constructor calls — replace `Map<> candidateInfo` arg with either `candidateId` (existing) or inline `CreateCandidateRequest` (new)
- Update `UpdateInterviewRequest` similarly
- Add tests for both candidate resolution paths:
  ```java
  @Test
  void createInterview_withCandidateId_usesExistingCandidate() {
      Candidate candidate = candidateRepository.save(
              new Candidate(null, "Test", "test@example.com", null, null, null));

      CreateInterviewRequest request = new CreateInterviewRequest();
      request.setInterviewerId(interviewer.getId());
      request.setCandidateId(candidate.getId());
      request.setTechStack("Java");
      request.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
      request.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

      Interview interview = interviewService.createInterview(request);

      assertEquals(candidate.getId(), interview.getCandidate().getId());
  }

  @Test
  void createInterview_withInlineCandidate_createsNewCandidate() {
      CreateCandidateRequest candidateReq = new CreateCandidateRequest(
              "New Candidate", "new@example.com", null, "Java", null);

      CreateInterviewRequest request = new CreateInterviewRequest();
      request.setInterviewerId(interviewer.getId());
      request.setCandidate(candidateReq);
      request.setTechStack("Java");
      request.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
      request.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

      Interview interview = interviewService.createInterview(request);

      assertNotNull(interview.getCandidate().getId());
      assertEquals("New Candidate", interview.getCandidate().getName());
  }

  @Test
  void createInterview_withTalentAcquisition_setsTaProfile() {
      Profile ta = new Profile(UUID.randomUUID(), "ta@gm2dev.com", "interviewer", null);
      profileRepository.save(ta);
      Candidate candidate = candidateRepository.save(
              new Candidate(null, "Test", "test@example.com", null, null, null));

      CreateInterviewRequest request = new CreateInterviewRequest();
      request.setInterviewerId(interviewer.getId());
      request.setCandidateId(candidate.getId());
      request.setTalentAcquisitionId(ta.getId());
      request.setTechStack("Java");
      request.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
      request.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

      Interview interview = interviewService.createInterview(request);

      assertEquals(ta.getId(), interview.getTalentAcquisition().getId());
  }
  ```

**Step 2: Update InterviewControllerTest**

- Update mock `CreateInterviewRequest` and `UpdateInterviewRequest` to use `candidateId` instead of `candidateInfo`
- Update `InterviewDto` construction in mocks to use `CandidateDto` instead of `Map`
- Add `@MockitoBean CandidateMapper candidateMapper` if needed by the mapper chain

**Step 3: Update GoogleCalendarServiceTest**

- Replace `interview.setCandidateInfo(Map.of(...))` with building a `Candidate` entity and calling `interview.setCandidate(candidate)`
- Update assertions for event description format (now structured instead of Map iteration)
- Update attendee extraction tests

**Step 4: Run all tests**

Run: `./gradlew test`
Expected: ALL tests PASS

**Step 5: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/InterviewServiceTest.java \
        src/test/java/com/gm2dev/interview_hub/controller/InterviewControllerTest.java \
        src/test/java/com/gm2dev/interview_hub/service/GoogleCalendarServiceTest.java
git commit -m "test: update existing tests for Candidate entity migration"
```

---

### Task 14: Clean Up — Remove JsonbConverter If Unused

**Files:**
- Check: `src/main/java/com/gm2dev/interview_hub/util/JsonbConverter.java`
- Check: `src/test/java/com/gm2dev/interview_hub/util/JsonbConverterTest.java`

**Step 1: Verify JsonbConverter is not used anywhere**

Run: `grep -r "JsonbConverter" src/main/java/` — if only found in `util/JsonbConverter.java` itself, it's unused.

Per CLAUDE.md: "A `JsonbConverter` also exists in `util/` but is **not** currently applied to any entity." Now that `candidateInfo` JSONB is gone, there's no remaining JSONB use case.

**Step 2: Delete both files**

```bash
rm src/main/java/com/gm2dev/interview_hub/util/JsonbConverter.java
rm src/test/java/com/gm2dev/interview_hub/util/JsonbConverterTest.java
```

**Step 3: Run all tests**

Run: `./gradlew test`
Expected: ALL tests PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove unused JsonbConverter"
```

---

### Task 15: Update Nginx Config and Security Config

**Files:**
- Modify: `frontend/nginx.conf` — add `/api/candidates` to the proxy routes
- Check: `src/main/java/com/gm2dev/interview_hub/config/SecurityConfig.java` — `/api/candidates` should already be covered by the catch-all authenticated rule, but verify

**Step 1: Update nginx.conf**

Add `/candidates` to the location block that proxies API requests. Look at the existing pattern — if it uses a single `/api/` prefix catch-all, no change needed. If routes are listed explicitly, add `/candidates`.

**Step 2: Run all tests one final time**

Run: `./gradlew test`
Expected: ALL tests PASS, 80%+ branch coverage

**Step 3: Commit**

```bash
git add frontend/nginx.conf
git commit -m "chore: add candidates route to nginx proxy"
```

---

### Task 16: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update the documentation**

- Add `Candidate` to the domain model section
- Update `Interview` description to mention `candidate` (ManyToOne) and `talentAcquisition` (ManyToOne) instead of `candidateInfo` JSONB
- Add `CandidateRepository`, `CandidateService`, `CandidateController` to relevant sections
- Add `CandidateDto`, `CreateCandidateRequest`, `UpdateCandidateRequest` to DTO section
- Update the JSONB section (remove or note that JSONB is no longer used)
- Add migration 004 to the schema management section

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for Candidate domain"
```
