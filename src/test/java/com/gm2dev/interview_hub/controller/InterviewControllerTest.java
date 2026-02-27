package com.gm2dev.interview_hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.service.InterviewService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.ActiveProfiles("test")
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES);

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    private Interview buildInterview() {
        UUID id = UUID.randomUUID();
        Profile interviewer = new Profile(UUID.randomUUID(), "test@example.com", "interviewer", null);
        interviewer.setGoogleSub("google-sub-123");
        interviewer.setGoogleAccessToken("encrypted-access-token");
        interviewer.setGoogleRefreshToken("encrypted-refresh-token");
        interviewer.setGoogleTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Interview interview = new Interview();
        interview.setId(id);
        interview.setInterviewer(interviewer);
        interview.setTechStack("Java");
        interview.setCandidateInfo(Map.of("name", "Jane Doe"));
        interview.setStartTime(start);
        interview.setEndTime(end);
        interview.setStatus(InterviewStatus.SCHEDULED);
        return interview;
    }

    @Test
    void createInterview_returns201() throws Exception {
        Interview interview = buildInterview();
        when(interviewService.createInterview(any(CreateInterviewRequest.class))).thenReturn(interview);

        String body = """
                {
                    "interviewerId": "%s",
                    "candidateInfo": {"name": "Jane"},
                    "techStack": "Java",
                    "startTime": "2026-03-15T10:00:00Z",
                    "endTime": "2026-03-15T11:00:00Z"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/interviews")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(interview.getId().toString()))
                .andExpect(jsonPath("$.techStack").value("Java"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void listInterviews_returns200WithPageStructure() throws Exception {
        Interview interview = buildInterview();
        Page<Interview> page = new PageImpl<>(List.of(interview));
        when(interviewService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/interviews")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(interview.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void getInterview_returns200() throws Exception {
        Interview interview = buildInterview();
        when(interviewService.findById(interview.getId())).thenReturn(interview);

        mockMvc.perform(get("/api/interviews/{id}", interview.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(interview.getId().toString()))
                .andExpect(jsonPath("$.techStack").value("Java"))
                .andExpect(jsonPath("$.interviewer.id").exists())
                .andExpect(jsonPath("$.interviewer.email").exists())
                .andExpect(jsonPath("$.interviewer.googleAccessToken").doesNotExist())
                .andExpect(jsonPath("$.interviewer.googleRefreshToken").doesNotExist())
                .andExpect(jsonPath("$.interviewer.googleTokenExpiry").doesNotExist())
                .andExpect(jsonPath("$.interviewer.googleSub").doesNotExist());
    }

    @Test
    void getInterview_withCyclicGraph_serializesWithoutInfiniteRecursion() throws Exception {
        Interview interview = buildInterview();

        ShadowingRequest request = new ShadowingRequest();
        request.setId(UUID.randomUUID());
        request.setInterview(interview);
        request.setShadower(new Profile(UUID.randomUUID(), "shadow@example.com", "interviewer", null));
        request.setStatus(ShadowingRequestStatus.PENDING);

        interview.setShadowingRequests(List.of(request));
        when(interviewService.findById(interview.getId())).thenReturn(interview);

        mockMvc.perform(get("/api/interviews/{id}", interview.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shadowingRequests[0].id").value(request.getId().toString()))
                .andExpect(jsonPath("$.shadowingRequests[0].interview").doesNotExist());
    }

    @Test
    void getInterview_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(interviewService.findById(id)).thenThrow(new EntityNotFoundException("Not found"));

        mockMvc.perform(get("/api/interviews/{id}", id)
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateInterview_returns200() throws Exception {
        Interview interview = buildInterview();
        when(interviewService.updateInterview(eq(interview.getId()), any(UpdateInterviewRequest.class)))
                .thenReturn(interview);

        String body = """
                {
                    "techStack": "Kotlin",
                    "startTime": "2026-04-15T14:00:00Z",
                    "endTime": "2026-04-15T15:00:00Z",
                    "status": "SCHEDULED"
                }
                """;

        mockMvc.perform(put("/api/interviews/{id}", interview.getId())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(interview.getId().toString()));
    }

    @Test
    void deleteInterview_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(interviewService).deleteInterview(id);

        mockMvc.perform(delete("/api/interviews/{id}", id)
                        .with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/interviews"))
                .andExpect(status().isUnauthorized());
    }
}
