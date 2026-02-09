package com.gm2dev.interview_hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.*;
import com.gm2dev.interview_hub.service.ShadowingRequestService;
import com.gm2dev.interview_hub.service.dto.CreateShadowingRequest;
import com.gm2dev.interview_hub.service.dto.RejectShadowingRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShadowingRequestController.class)
@Import(SecurityConfig.class)
class ShadowingRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES);

    @MockitoBean
    private ShadowingRequestService shadowingRequestService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ShadowingRequest buildShadowingRequest(ShadowingRequestStatus status) {
        Profile shadower = new Profile(UUID.randomUUID(), "shadower@example.com", "interviewer", null);
        Profile interviewer = new Profile(UUID.randomUUID(), "interviewer@example.com", "interviewer", null);

        Interview interview = new Interview();
        interview.setId(UUID.randomUUID());
        interview.setInterviewer(interviewer);
        interview.setTechStack("Java");
        interview.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        interview.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        interview.setStatus(InterviewStatus.SCHEDULED);

        ShadowingRequest request = new ShadowingRequest();
        request.setId(UUID.randomUUID());
        request.setInterview(interview);
        request.setShadower(shadower);
        request.setStatus(status);
        return request;
    }

    @Test
    void requestShadowing_returns201() throws Exception {
        UUID interviewId = UUID.randomUUID();
        UUID shadowerId = UUID.randomUUID();
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.PENDING);

        when(shadowingRequestService.requestShadowing(interviewId, shadowerId)).thenReturn(shadowingRequest);

        CreateShadowingRequest request = new CreateShadowingRequest(shadowerId);

        mockMvc.perform(post("/api/interviews/{interviewId}/shadowing-requests", interviewId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(shadowingRequest.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void cancelShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.CANCELLED);

        when(shadowingRequestService.cancelShadowingRequest(shadowingRequest.getId())).thenReturn(shadowingRequest);

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", shadowingRequest.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void approveShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.APPROVED);

        when(shadowingRequestService.approveShadowingRequest(shadowingRequest.getId())).thenReturn(shadowingRequest);

        mockMvc.perform(post("/api/shadowing-requests/{id}/approve", shadowingRequest.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.REJECTED);
        shadowingRequest.setReason("Full capacity");

        when(shadowingRequestService.rejectShadowingRequest(shadowingRequest.getId(), "Full capacity"))
                .thenReturn(shadowingRequest);

        RejectShadowingRequest request = new RejectShadowingRequest("Full capacity");

        mockMvc.perform(post("/api/shadowing-requests/{id}/reject", shadowingRequest.getId())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Full capacity"));
    }

    @Test
    void rejectShadowingRequest_withoutReason_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.REJECTED);

        when(shadowingRequestService.rejectShadowingRequest(shadowingRequest.getId(), null))
                .thenReturn(shadowingRequest);

        RejectShadowingRequest request = new RejectShadowingRequest();

        mockMvc.perform(post("/api/shadowing-requests/{id}/reject", shadowingRequest.getId())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancelShadowingRequest_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shadowingRequestService.cancelShadowingRequest(id))
                .thenThrow(new EntityNotFoundException("Not found"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", id)
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelShadowingRequest_notPending_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(shadowingRequestService.cancelShadowingRequest(id))
                .thenThrow(new IllegalStateException("Not in PENDING status"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", id)
                        .with(jwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/interviews/" + UUID.randomUUID() + "/shadowing-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shadowerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
