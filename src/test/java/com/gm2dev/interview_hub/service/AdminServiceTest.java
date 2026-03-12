package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(profileRepository, passwordEncoder, emailService);
    }

    @Test
    void createUser_withValidRequest_createsProfileAndSendsEmail() {
        CreateUserRequest request = new CreateUserRequest("new@gm2dev.com", "interviewer");
        when(profileRepository.findByEmail("new@gm2dev.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.createUser(request);

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertEquals("new@gm2dev.com", saved.getEmail());
        assertEquals("interviewer", saved.getRole());
        assertTrue(saved.isEmailVerified());
        assertNotNull(saved.getPasswordHash());

        verify(emailService).sendTemporaryPasswordEmail(eq("new@gm2dev.com"), anyString());
    }

    @Test
    void createUser_withExistingEmail_throwsIllegalStateException() {
        CreateUserRequest request = new CreateUserRequest("existing@gm2dev.com", "interviewer");
        when(profileRepository.findByEmail("existing@gm2dev.com")).thenReturn(Optional.of(new Profile()));
        assertThrows(IllegalStateException.class, () -> adminService.createUser(request));
    }

    @Test
    void createUser_withNonGm2devEmail_throwsSecurityException() {
        CreateUserRequest request = new CreateUserRequest("user@gmail.com", "interviewer");
        assertThrows(SecurityException.class, () -> adminService.createUser(request));
    }

    @Test
    void updateRole_withValidId_updatesRole() {
        UUID id = UUID.randomUUID();
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole("interviewer");

        when(profileRepository.findById(id)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.updateRole(id, "admin");

        assertEquals("admin", profile.getRole());
        verify(profileRepository).save(profile);
    }

    @Test
    void updateRole_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> adminService.updateRole(id, "admin"));
    }

    @Test
    void deleteUser_withValidId_deletesProfile() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);
        adminService.deleteUser(id);
        verify(profileRepository).deleteById(id);
    }

    @Test
    void deleteUser_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(false);
        assertThrows(EntityNotFoundException.class, () -> adminService.deleteUser(id));
    }
}
