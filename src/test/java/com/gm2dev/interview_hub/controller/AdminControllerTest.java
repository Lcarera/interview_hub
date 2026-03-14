package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void listUsers_asAdmin_returns200() throws Exception {
        when(adminService.listUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void listUsers_asInterviewer_returns403() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_interviewer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        ProfileDto dto = new ProfileDto(UUID.randomUUID(), "new@gm2dev.com", Role.interviewer, "new@gm2dev.com");

        when(adminService.createUser(any(CreateUserRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@gm2dev.com\", \"role\": \"interviewer\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createUser_asInterviewer_returns403() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_interviewer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@gm2dev.com\", \"role\": \"interviewer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_withInvalidRole_returns400() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@gm2dev.com\", \"role\": \"superadmin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_asAdmin_returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/admin/users/" + userId + "/role")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"admin\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUser_asAdmin_returns204() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/admin/users/" + userId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isNoContent());
    }
}
