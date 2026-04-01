package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.dto.CandidateDto;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import com.gm2dev.interview_hub.mapper.CandidateMapper;
import com.gm2dev.interview_hub.service.CandidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createCandidate_returns201() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Jane Doe", "jane@example.com", "https://linkedin.com/in/janedoe", "Java", "https://feedback.link/123"
        );
        Candidate candidate = buildCandidate();
        CandidateDto dto = buildCandidateDto(candidate.getId());

        when(candidateService.createCandidate(any(CandidateRequest.class))).thenReturn(candidate);
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
        CandidateRequest request = new CandidateRequest(
                "Jane Smith", "jane.smith@example.com", null, "React", null
        );
        Candidate updated = buildCandidate();
        updated.setId(id);
        updated.setName("Jane Smith");
        CandidateDto dto = new CandidateDto(id, "Jane Smith", "jane.smith@example.com", null, "React", null);

        when(candidateService.updateCandidate(eq(id), any(CandidateRequest.class))).thenReturn(updated);
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
    void deleteCandidate_withExistingInterviews_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("Cannot delete candidate with existing interviews"))
                .when(candidateService).deleteCandidate(id);

        mockMvc.perform(delete("/api/candidates/{id}", id)
                        .with(jwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCandidate_dataIntegrityViolation_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("FK constraint violation"))
                .when(candidateService).deleteCandidate(id);

        mockMvc.perform(delete("/api/candidates/{id}", id)
                        .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Operation conflicts with existing data"));
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
