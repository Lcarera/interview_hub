# MapStruct Integration Design

**Date:** 2026-02-28
**Scope:** Backend — bidirectional entity ↔ DTO mapping using MapStruct 1.6.3

## Goal

Decouple the API contract from JPA entities by introducing response DTOs for all three domain objects (`Interview`, `ShadowingRequest`, `Profile`) and using MapStruct for both entity→DTO (in controllers) and DTO→entity update (in service). Existing request DTOs (`CreateInterviewRequest`, `UpdateInterviewRequest`, `RejectShadowingRequest`) are unchanged.

## Approach

Approach B (bidirectional): response DTOs + `@MappingTarget` for updates. Controllers map entity → response DTO. `InterviewService.updateInterview` uses `@MappingTarget` to replace manual field assignments. Create operations keep existing service signatures (DB relation resolution prevents pure mapper usage there).

## Dependencies

Add to `build.gradle`:

```groovy
implementation 'org.mapstruct:mapstruct:1.6.3'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

`lombok-mapstruct-binding` ensures Lombok runs before MapStruct during annotation processing.

All mappers use `componentModel = "spring"` (injected as Spring beans).

## Response DTOs (`dto/` package)

| Class | Fields |
|---|---|
| `ProfileDto` | `UUID id, String email, String role, String calendarEmail` |
| `InterviewSummaryDto` | `UUID id, String techStack, Instant startTime, Instant endTime, InterviewStatus status` |
| `ShadowingRequestSummaryDto` | `UUID id, ShadowingRequestStatus status, ProfileDto shadower` |
| `InterviewDto` | `UUID id, ProfileDto interviewer, Map<String,Object> candidateInfo, String techStack, Instant startTime, Instant endTime, InterviewStatus status, List<ShadowingRequestSummaryDto> shadowingRequests` |
| `ShadowingRequestDto` | `UUID id, InterviewSummaryDto interview, ProfileDto shadower, ShadowingRequestStatus status, String reason` |

All use Lombok `@Value` (immutable). Sensitive `Profile` fields (`googleSub`, `googleAccessToken`, `googleRefreshToken`, `googleTokenExpiry`) are absent by design — not mapped.

### Circular Reference Strategy

`InterviewDto` embeds `List<ShadowingRequestSummaryDto>` (no `interview` back-reference).
`ShadowingRequestDto` embeds `InterviewSummaryDto` (no `shadowingRequests` list).
The `@JsonIgnoreProperties` annotations on entities can be removed once controllers no longer return raw entities.

## Mappers (`mapper/` package)

### `ProfileMapper`
- Standalone (no `uses`)
- `ProfileDto toDto(Profile profile)`

### `ShadowingRequestMapper`
- `uses = {ProfileMapper.class}`
- `ShadowingRequestDto toDto(ShadowingRequest request)`
- `ShadowingRequestSummaryDto toSummaryDto(ShadowingRequest request)`
- `interview → InterviewSummaryDto` is auto-generated inline (all scalar fields, no nested objects) — does **not** use `InterviewMapper`, avoiding a circular Spring bean dependency

### `InterviewMapper`
- `uses = {ProfileMapper.class, ShadowingRequestMapper.class}`
- `InterviewDto toDto(Interview interview)`
- `InterviewSummaryDto toSummaryDto(Interview interview)`
- `void updateFromRequest(UpdateInterviewRequest request, @MappingTarget Interview interview)`

Dependency chain: `InterviewMapper → {ProfileMapper, ShadowingRequestMapper} → {ProfileMapper}`. No cycle.

## Controller Changes

Each controller gains a constructor-injected mapper. Return types change:

| Controller | Old return | New return |
|---|---|---|
| `InterviewController.createInterview` | `Interview` | `InterviewDto` |
| `InterviewController.listInterviews` | `Page<Interview>` | `Page<InterviewDto>` |
| `InterviewController.getInterview` | `Interview` | `InterviewDto` |
| `InterviewController.updateInterview` | `Interview` | `InterviewDto` |
| `InterviewController.deleteInterview` | `void` | `void` (unchanged) |
| `ShadowingRequestController.*` | `ShadowingRequest` / `List<ShadowingRequest>` | `ShadowingRequestDto` / `List<ShadowingRequestDto>` |
| `ProfileController.*` | `Profile` / `List<Profile>` | `ProfileDto` / `List<ProfileDto>` |

Page mapping: `page.map(interviewMapper::toDto)`.

## Service Changes

`InterviewService` injects `InterviewMapper`. In `updateInterview`, replace:
```java
interview.setCandidateInfo(request.getCandidateInfo());
interview.setTechStack(request.getTechStack());
interview.setStartTime(request.getStartTime());
interview.setEndTime(request.getEndTime());
interview.setStatus(request.getStatus());
```
with:
```java
interviewMapper.updateFromRequest(request, interview);
```

All other service methods unchanged.

## Test Changes

`@WebMvcTest` does not auto-scan `@Component` beans outside the web layer. Add mapper implementations to `@Import`:

```java
@Import({SecurityConfig.class, InterviewMapperImpl.class,
         ProfileMapperImpl.class, ShadowingRequestMapperImpl.class})
```

`@SpringBootTest` service tests pick up mappers automatically — no changes needed there.

Controller test mock setups remain valid: services still return entities, mapping happens in the controller after the mock returns.

## New Files

- `src/main/java/com/gm2dev/interview_hub/dto/ProfileDto.java`
- `src/main/java/com/gm2dev/interview_hub/dto/InterviewDto.java`
- `src/main/java/com/gm2dev/interview_hub/dto/InterviewSummaryDto.java`
- `src/main/java/com/gm2dev/interview_hub/dto/ShadowingRequestDto.java`
- `src/main/java/com/gm2dev/interview_hub/dto/ShadowingRequestSummaryDto.java`
- `src/main/java/com/gm2dev/interview_hub/mapper/ProfileMapper.java`
- `src/main/java/com/gm2dev/interview_hub/mapper/InterviewMapper.java`
- `src/main/java/com/gm2dev/interview_hub/mapper/ShadowingRequestMapper.java`

## Modified Files

- `build.gradle`
- `controller/InterviewController.java`
- `controller/ShadowingRequestController.java`
- `controller/ProfileController.java`
- `service/InterviewService.java`
- `test/controller/InterviewControllerTest.java`
- `test/controller/ShadowingRequestControllerTest.java`
- `test/controller/ProfileControllerTest.java`
