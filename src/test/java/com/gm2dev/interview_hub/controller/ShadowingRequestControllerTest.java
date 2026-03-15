package com.gm2dev.interview_hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.*;
import com.gm2dev.interview_hub.mapper.ProfileMapperImpl;
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapperImpl;
import com.gm2dev.interview_hub.dto.RejectShadowingRequest;
import com.gm2dev.interview_hub.service.ShadowingRequestService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShadowingRequestController.class)
@Import({SecurityConfig.class, ShadowingRequestMapperImpl.class, ProfileMapperImpl.class})
@org.springframework.test.context.ActiveProfiles("test")
class ShadowingRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES);

    @MockitoBean
    private ShadowingRequestService shadowingRequestService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    private ShadowingRequest buildShadowingRequest(ShadowingRequestStatus status) {
        Profile shadower = new Profile(UUID.randomUUID(), "shadower@example.com", Role.interviewer);
        Profile interviewer = new Profile(UUID.randomUUID(), "interviewer@example.com", Role.interviewer);

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

        mockMvc.perform(post("/api/interviews/{interviewId}/shadowing-requests", interviewId)
                        .with(jwt().jwt(j -> j.subject(shadowerId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(shadowingRequest.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.interview.interviewer").doesNotExist());
    }

    @Test
    void requestShadowing_withCyclicGraph_serializesWithoutInfiniteRecursion() throws Exception {
        UUID interviewId = UUID.randomUUID();
        UUID shadowerId = UUID.randomUUID();
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.PENDING);

        shadowingRequest.getInterview().setShadowingRequests(java.util.List.of(shadowingRequest));
        when(shadowingRequestService.requestShadowing(interviewId, shadowerId)).thenReturn(shadowingRequest);

        mockMvc.perform(post("/api/interviews/{interviewId}/shadowing-requests", interviewId)
                        .with(jwt().jwt(j -> j.subject(shadowerId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interview.id").value(shadowingRequest.getInterview().getId().toString()))
                .andExpect(jsonPath("$.interview.shadowingRequests[0].interview").doesNotExist());
    }

    @Test
    void cancelShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.CANCELLED);

        when(shadowingRequestService.cancelShadowingRequest(eq(shadowingRequest.getId()), any(UUID.class))).thenReturn(shadowingRequest);

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", shadowingRequest.getId())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void approveShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.APPROVED);

        when(shadowingRequestService.approveShadowingRequest(eq(shadowingRequest.getId()), any(UUID.class))).thenReturn(shadowingRequest);

        mockMvc.perform(post("/api/shadowing-requests/{id}/approve", shadowingRequest.getId())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectShadowingRequest_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.REJECTED);
        shadowingRequest.setReason("Full capacity");

        when(shadowingRequestService.rejectShadowingRequest(eq(shadowingRequest.getId()), eq("Full capacity"), any(UUID.class)))
                .thenReturn(shadowingRequest);

        RejectShadowingRequest request = new RejectShadowingRequest("Full capacity");

        mockMvc.perform(post("/api/shadowing-requests/{id}/reject", shadowingRequest.getId())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Full capacity"));
    }

    @Test
    void rejectShadowingRequest_withoutReason_returns200() throws Exception {
        ShadowingRequest shadowingRequest = buildShadowingRequest(ShadowingRequestStatus.REJECTED);

        when(shadowingRequestService.rejectShadowingRequest(eq(shadowingRequest.getId()), eq(null), any(UUID.class)))
                .thenReturn(shadowingRequest);

        RejectShadowingRequest request = new RejectShadowingRequest();

        mockMvc.perform(post("/api/shadowing-requests/{id}/reject", shadowingRequest.getId())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancelShadowingRequest_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shadowingRequestService.cancelShadowingRequest(eq(id), any(UUID.class)))
                .thenThrow(new EntityNotFoundException("Not found"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelShadowingRequest_notPending_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(shadowingRequestService.cancelShadowingRequest(eq(id), any(UUID.class)))
                .thenThrow(new IllegalStateException("Not in PENDING status"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void listByInterview_returns200() throws Exception {
        UUID interviewId = UUID.randomUUID();
        ShadowingRequest sr = buildShadowingRequest(ShadowingRequestStatus.PENDING);
        when(shadowingRequestService.findByInterviewId(interviewId)).thenReturn(java.util.List.of(sr));

        mockMvc.perform(get("/api/interviews/{interviewId}/shadowing-requests", interviewId)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void listMyShadowingRequests_returns200() throws Exception {
        UUID shadowerId = UUID.randomUUID();
        ShadowingRequest sr = buildShadowingRequest(ShadowingRequestStatus.APPROVED);
        when(shadowingRequestService.findByShadowerId(shadowerId)).thenReturn(java.util.List.of(sr));

        mockMvc.perform(get("/api/shadowing-requests/my")
                        .with(jwt().jwt(j -> j.subject(shadowerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void cancelShadowingRequest_byNonShadower_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        UUID nonShadowerId = UUID.randomUUID();
        when(shadowingRequestService.cancelShadowingRequest(eq(id), eq(nonShadowerId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not the shadower"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/cancel", id)
                        .with(jwt().jwt(j -> j.subject(nonShadowerId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveShadowingRequest_byNonInterviewer_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        UUID nonInterviewerId = UUID.randomUUID();
        when(shadowingRequestService.approveShadowingRequest(eq(id), eq(nonInterviewerId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not the interviewer"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/approve", id)
                        .with(jwt().jwt(j -> j.subject(nonInterviewerId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectShadowingRequest_byNonInterviewer_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        UUID nonInterviewerId = UUID.randomUUID();
        when(shadowingRequestService.rejectShadowingRequest(eq(id), any(), eq(nonInterviewerId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not the interviewer"));

        mockMvc.perform(post("/api/shadowing-requests/{id}/reject", id)
                        .with(jwt().jwt(j -> j.subject(nonInterviewerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/interviews/" + UUID.randomUUID() + "/shadowing-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shadowerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
