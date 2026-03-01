# MapStruct Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce MapStruct 1.6.3 to map JPA entities to response DTOs and vice versa, decoupling the API contract from the persistence layer.

**Architecture:** Five response DTOs with shallow nesting break the Interview↔ShadowingRequest circular reference. Three mapper interfaces (`ProfileMapper`, `ShadowingRequestMapper`, `InterviewMapper`) are Spring beans injected into controllers for entity→DTO conversion. `InterviewService` injects `InterviewMapper` to replace manual field assignments in `updateInterview`. Existing request DTOs are unchanged.

**Tech Stack:** Spring Boot 4.0.2, MapStruct 1.6.3, Lombok with `lombok-mapstruct-binding`, Java 25, JUnit 5 + `@WebMvcTest`.

---

## Worktree Setup (before any tasks)

```bash
git worktree add .worktrees/feature-mapstruct -b feature/mapstruct-dtos
cd .worktrees/feature-mapstruct
./gradlew test   # verify clean baseline — all tests should pass
```

---

## Task 1: Add MapStruct Dependency

**Files:**
- Modify: `build.gradle`

### Step 1: Add the three entries to `build.gradle`

In the `dependencies { }` block, after the Lombok entries:

```groovy
// MapStruct
implementation 'org.mapstruct:mapstruct:1.6.3'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

`lombok-mapstruct-binding` ensures Lombok runs before MapStruct during annotation processing — required when both processors are active.

### Step 2: Run tests

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL` — all existing tests pass.

### Step 3: Commit

```bash
git add build.gradle
git commit -m "build: add MapStruct 1.6.3 dependency"
```

---

## Task 2: Create Response DTOs

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/dto/ProfileDto.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/InterviewSummaryDto.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/ShadowingRequestSummaryDto.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/InterviewDto.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/ShadowingRequestDto.java`

All five are immutable value objects (`@Value`) — no logic, no unit tests needed beyond compilation.

### Step 1: Create `ProfileDto.java`

```java
package com.gm2dev.interview_hub.dto;

import lombok.Value;
import java.util.UUID;

@Value
public class ProfileDto {
    UUID id;
    String email;
    String role;
    String calendarEmail;
}
```

### Step 2: Create `InterviewSummaryDto.java`

Shallow interview reference used inside `ShadowingRequestDto` — no interviewer, no shadowing list.

```java
package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import lombok.Value;
import java.time.Instant;
import java.util.UUID;

@Value
public class InterviewSummaryDto {
    UUID id;
    String techStack;
    Instant startTime;
    Instant endTime;
    InterviewStatus status;
}
```

### Step 3: Create `ShadowingRequestSummaryDto.java`

Shallow shadowing reference used inside `InterviewDto` — no back-reference to interview.

```java
package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import lombok.Value;
import java.util.UUID;

@Value
public class ShadowingRequestSummaryDto {
    UUID id;
    ShadowingRequestStatus status;
    ProfileDto shadower;
}
```

### Step 4: Create `InterviewDto.java`

```java
package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import lombok.Value;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class InterviewDto {
    UUID id;
    ProfileDto interviewer;
    Map<String, Object> candidateInfo;
    String techStack;
    Instant startTime;
    Instant endTime;
    InterviewStatus status;
    List<ShadowingRequestSummaryDto> shadowingRequests;
}
```

### Step 5: Create `ShadowingRequestDto.java`

```java
package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import lombok.Value;
import java.util.UUID;

@Value
public class ShadowingRequestDto {
    UUID id;
    InterviewSummaryDto interview;
    ProfileDto shadower;
    ShadowingRequestStatus status;
    String reason;
}
```

### Step 6: Verify compilation

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

### Step 7: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/dto/
git commit -m "feat: add response DTOs for Profile, Interview, and ShadowingRequest"
```

---

## Task 3: Create Mappers

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/mapper/ProfileMapper.java`
- Create: `src/main/java/com/gm2dev/interview_hub/mapper/ShadowingRequestMapper.java`
- Create: `src/main/java/com/gm2dev/interview_hub/mapper/InterviewMapper.java`

MapStruct generates implementations at compile time. No unit tests needed — mappers are tested via controller tests in Tasks 4–6.

### Step 1: Create `ProfileMapper.java`

```java
package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.ProfileDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    ProfileDto toDto(Profile profile);
}
```

### Step 2: Create `ShadowingRequestMapper.java`

```java
package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.dto.ShadowingRequestDto;
import com.gm2dev.interview_hub.dto.ShadowingRequestSummaryDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ProfileMapper.class})
public interface ShadowingRequestMapper {
    ShadowingRequestDto toDto(ShadowingRequest request);
    ShadowingRequestSummaryDto toSummaryDto(ShadowingRequest request);
}
```

Note: `interview → InterviewSummaryDto` is auto-generated inline by MapStruct (all scalar fields match by name). No `InterviewMapper` reference needed here — this avoids a circular Spring bean dependency.

### Step 3: Create `InterviewMapper.java`

```java
package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.dto.InterviewSummaryDto;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {ProfileMapper.class, ShadowingRequestMapper.class})
public interface InterviewMapper {
    InterviewDto toDto(Interview interview);
    InterviewSummaryDto toSummaryDto(Interview interview);
    void updateFromRequest(UpdateInterviewRequest request, @MappingTarget Interview interview);
}
```

Dependency chain: `InterviewMapper → {ProfileMapper, ShadowingRequestMapper} → {ProfileMapper}`. No cycle.

`updateFromRequest` maps `candidateInfo`, `techStack`, `startTime`, `endTime`, `status` — all names match. Fields not in `UpdateInterviewRequest` (`id`, `interviewer`, `googleEventId`, `shadowingRequests`) are left untouched.

### Step 4: Verify compilation and check generated files

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`. Verify generated files exist:
```bash
ls build/generated/sources/annotationProcessor/java/main/com/gm2dev/interview_hub/mapper/
```
Expected: `InterviewMapperImpl.java`, `ProfileMapperImpl.java`, `ShadowingRequestMapperImpl.java`

### Step 5: Run full tests

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL`

### Step 6: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/mapper/
git commit -m "feat: add MapStruct mappers for Profile, Interview, and ShadowingRequest"
```

---

## Task 4: ProfileController — return ProfileDto

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/controller/ProfileControllerTest.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/controller/ProfileController.java`

### Step 1: Update `@Import` in `ProfileControllerTest`

Replace:
```java
@Import(SecurityConfig.class)
```
with:
```java
@Import({SecurityConfig.class, ProfileMapperImpl.class})
```

Add import at the top of the file:
```java
import com.gm2dev.interview_hub.mapper.ProfileMapperImpl;
```

### Step 2: Run tests — should still pass (controller unchanged)

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.ProfileControllerTest"
```
Expected: `BUILD SUCCESSFUL`

### Step 3: Update `ProfileController`

Add imports:
```java
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
```

Add `ProfileMapper` to the constructor-injected fields (Lombok `@RequiredArgsConstructor` picks it up automatically):
```java
private final ProfileMapper profileMapper;
```

Replace both method signatures and bodies:
```java
@GetMapping("/me")
public ProfileDto getMyProfile(@AuthenticationPrincipal Jwt jwt) {
    UUID profileId = UUID.fromString(jwt.getSubject());
    return profileMapper.toDto(profileService.findById(profileId));
}

@GetMapping
public List<ProfileDto> listProfiles() {
    return profileService.findAll().stream()
            .map(profileMapper::toDto)
            .toList();
}
```

Remove the unused `import com.gm2dev.interview_hub.domain.Profile;` import.

### Step 4: Run tests to verify they pass

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.ProfileControllerTest"
```
Expected: `BUILD SUCCESSFUL` — all 4 tests pass.

### Step 5: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/controller/ProfileController.java \
        src/test/java/com/gm2dev/interview_hub/controller/ProfileControllerTest.java
git commit -m "feat: ProfileController returns ProfileDto via MapStruct"
```

---

## Task 5: InterviewController — return InterviewDto (TDD)

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/controller/InterviewControllerTest.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/controller/InterviewController.java`

### Step 1: Update `@Import` in `InterviewControllerTest`

Replace:
```java
@Import(SecurityConfig.class)
```
with:
```java
@Import({SecurityConfig.class, InterviewMapperImpl.class, ProfileMapperImpl.class, ShadowingRequestMapperImpl.class})
```

Add imports:
```java
import com.gm2dev.interview_hub.mapper.InterviewMapperImpl;
import com.gm2dev.interview_hub.mapper.ProfileMapperImpl;
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapperImpl;
```

### Step 2: Add a failing assertion to `getInterview_returns200`

`Interview` entity has a `googleEventId` field that IS serialized today. `InterviewDto` deliberately omits it. Add this assertion at the end of the `getInterview_returns200` test to get a red/green cycle:

```java
.andExpect(jsonPath("$.googleEventId").doesNotExist());
```

### Step 3: Run to verify the assertion fails

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.InterviewControllerTest.getInterview_returns200"
```
Expected: FAIL — `$.googleEventId` currently EXISTS in the entity JSON.

### Step 4: Update `InterviewController`

Add imports:
```java
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.mapper.InterviewMapper;
```

Add to constructor-injected fields:
```java
private final InterviewMapper interviewMapper;
```

Replace the four non-void methods (leave `deleteInterview` unchanged):
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public InterviewDto createInterview(@Valid @RequestBody CreateInterviewRequest request) {
    return interviewMapper.toDto(interviewService.createInterview(request));
}

@GetMapping
public Page<InterviewDto> listInterviews(Pageable pageable) {
    return interviewService.findAll(pageable).map(interviewMapper::toDto);
}

@GetMapping("/{id}")
public InterviewDto getInterview(@PathVariable UUID id) {
    return interviewMapper.toDto(interviewService.findById(id));
}

@PutMapping("/{id}")
public InterviewDto updateInterview(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateInterviewRequest request) {
    return interviewMapper.toDto(interviewService.updateInterview(id, request));
}
```

Remove the unused `import com.gm2dev.interview_hub.domain.Interview;` import.

### Step 5: Run all `InterviewControllerTest` tests

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.InterviewControllerTest"
```
Expected: `BUILD SUCCESSFUL` — all tests pass including the new `$.googleEventId` assertion.

### Step 6: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/controller/InterviewController.java \
        src/test/java/com/gm2dev/interview_hub/controller/InterviewControllerTest.java
git commit -m "feat: InterviewController returns InterviewDto via MapStruct"
```

---

## Task 6: ShadowingRequestController — return ShadowingRequestDto (TDD)

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/controller/ShadowingRequestControllerTest.java`
- Modify: `src/main/java/com/gm2dev/interview_hub/controller/ShadowingRequestController.java`

### Step 1: Update `@Import` in `ShadowingRequestControllerTest`

Replace:
```java
@Import(SecurityConfig.class)
```
with:
```java
@Import({SecurityConfig.class, ShadowingRequestMapperImpl.class, ProfileMapperImpl.class})
```

Add imports:
```java
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapperImpl;
import com.gm2dev.interview_hub.mapper.ProfileMapperImpl;
```

### Step 2: Add a failing assertion to `requestShadowing_returns201`

`ShadowingRequest` entity serializes `$.interview.interviewer` (a full `Profile`). `ShadowingRequestDto.interview` is `InterviewSummaryDto` which has no `interviewer` field. Add at the end of `requestShadowing_returns201`:

```java
.andExpect(jsonPath("$.interview.interviewer").doesNotExist());
```

### Step 3: Run to verify the assertion fails

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.ShadowingRequestControllerTest.requestShadowing_returns201"
```
Expected: FAIL — `$.interview.interviewer` currently EXISTS.

### Step 4: Update `ShadowingRequestController`

Add imports:
```java
import com.gm2dev.interview_hub.dto.ShadowingRequestDto;
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapper;
```

Add to constructor-injected fields:
```java
private final ShadowingRequestMapper shadowingRequestMapper;
```

Replace all six methods:
```java
@GetMapping("/api/interviews/{interviewId}/shadowing-requests")
public List<ShadowingRequestDto> listByInterview(@PathVariable UUID interviewId) {
    return shadowingRequestService.findByInterviewId(interviewId).stream()
            .map(shadowingRequestMapper::toDto)
            .toList();
}

@GetMapping("/api/shadowing-requests/my")
public List<ShadowingRequestDto> listMyShadowingRequests(@AuthenticationPrincipal Jwt jwt) {
    UUID shadowerId = UUID.fromString(jwt.getSubject());
    return shadowingRequestService.findByShadowerId(shadowerId).stream()
            .map(shadowingRequestMapper::toDto)
            .toList();
}

@PostMapping("/api/interviews/{interviewId}/shadowing-requests")
@ResponseStatus(HttpStatus.CREATED)
public ShadowingRequestDto requestShadowing(@PathVariable UUID interviewId,
                                            @AuthenticationPrincipal Jwt jwt) {
    UUID shadowerId = UUID.fromString(jwt.getSubject());
    return shadowingRequestMapper.toDto(
            shadowingRequestService.requestShadowing(interviewId, shadowerId));
}

@PostMapping("/api/shadowing-requests/{id}/cancel")
public ShadowingRequestDto cancelShadowingRequest(@PathVariable UUID id) {
    return shadowingRequestMapper.toDto(shadowingRequestService.cancelShadowingRequest(id));
}

@PostMapping("/api/shadowing-requests/{id}/approve")
public ShadowingRequestDto approveShadowingRequest(@PathVariable UUID id) {
    return shadowingRequestMapper.toDto(shadowingRequestService.approveShadowingRequest(id));
}

@PostMapping("/api/shadowing-requests/{id}/reject")
public ShadowingRequestDto rejectShadowingRequest(@PathVariable UUID id,
                                                   @RequestBody RejectShadowingRequest request) {
    return shadowingRequestMapper.toDto(
            shadowingRequestService.rejectShadowingRequest(id, request.getReason()));
}
```

Remove the unused `import com.gm2dev.interview_hub.domain.ShadowingRequest;` import.

### Step 5: Run all `ShadowingRequestControllerTest` tests

```bash
./gradlew test --tests "com.gm2dev.interview_hub.controller.ShadowingRequestControllerTest"
```
Expected: `BUILD SUCCESSFUL` — all tests pass.

### Step 6: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/controller/ShadowingRequestController.java \
        src/test/java/com/gm2dev/interview_hub/controller/ShadowingRequestControllerTest.java
git commit -m "feat: ShadowingRequestController returns ShadowingRequestDto via MapStruct"
```

---

## Task 7: InterviewService — use `@MappingTarget` for updates

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/InterviewService.java`

### Step 1: Update `InterviewService`

Add import:
```java
import com.gm2dev.interview_hub.mapper.InterviewMapper;
```

Add `InterviewMapper` to the constructor-injected fields:
```java
private final InterviewMapper interviewMapper;
```

In `updateInterview`, replace the five manual `setX()` calls:
```java
// Remove:
interview.setCandidateInfo(request.getCandidateInfo());
interview.setTechStack(request.getTechStack());
interview.setStartTime(request.getStartTime());
interview.setEndTime(request.getEndTime());
interview.setStatus(request.getStatus());

// Replace with:
interviewMapper.updateFromRequest(request, interview);
```

Everything else in `updateInterview` (the save, the calendar update call) stays unchanged.

### Step 2: Run the full test suite

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL` — all tests pass, JaCoCo 80% branch coverage passes.

### Step 3: Commit

```bash
git add src/main/java/com/gm2dev/interview_hub/service/InterviewService.java
git commit -m "refactor: use MapStruct @MappingTarget in InterviewService.updateInterview"
```

---

## Verification

```bash
./gradlew test
```
All tests pass. Verify generated mappers exist:
```bash
ls build/generated/sources/annotationProcessor/java/main/com/gm2dev/interview_hub/mapper/
```
Expected: `InterviewMapperImpl.java`, `ProfileMapperImpl.java`, `ShadowingRequestMapperImpl.java`
