package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.service.ProfileService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void getMyProfile_returns200() throws Exception {
        UUID profileId = UUID.randomUUID();
        Profile profile = new Profile(profileId, "test@gm2dev.com", "interviewer", null);
        when(profileService.findById(profileId)).thenReturn(profile);

        mockMvc.perform(get("/api/profiles/me")
                        .with(jwt().jwt(j -> j.subject(profileId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.email").value("test@gm2dev.com"))
                .andExpect(jsonPath("$.role").value("interviewer"));
    }

    @Test
    void getMyProfile_notFound_returns404() throws Exception {
        UUID profileId = UUID.randomUUID();
        when(profileService.findById(profileId))
                .thenThrow(new EntityNotFoundException("Profile not found: " + profileId));

        mockMvc.perform(get("/api/profiles/me")
                        .with(jwt().jwt(j -> j.subject(profileId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProfiles_returns200() throws Exception {
        Profile p1 = new Profile(UUID.randomUUID(), "a@gm2dev.com", "interviewer", null);
        Profile p2 = new Profile(UUID.randomUUID(), "b@gm2dev.com", "interviewer", null);
        when(profileService.findAll()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/profiles")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("a@gm2dev.com"));
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/profiles/me"))
                .andExpect(status().isUnauthorized());
    }
}
