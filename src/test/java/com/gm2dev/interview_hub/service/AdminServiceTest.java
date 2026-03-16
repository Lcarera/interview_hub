package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.ShadowingRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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

    @Mock
    private ProfileMapper profileMapper;

    @Mock
    private InterviewRepository interviewRepository;

    @Mock
    private ShadowingRequestRepository shadowingRequestRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(profileRepository, passwordEncoder, emailService, profileMapper, interviewRepository, shadowingRequestRepository);
    }

    @Test
    void listUsers_returnsPageOfProfileDto() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setRole(Role.interviewer);

        ProfileDto dto = new ProfileDto(profile.getId(), profile.getEmail(), Role.interviewer);

        Pageable pageable = PageRequest.of(0, 20);
        when(profileRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(profile)));
        when(profileMapper.toDto(profile)).thenReturn(dto);

        Page<ProfileDto> result = adminService.listUsers(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(dto, result.getContent().getFirst());
    }

    @Test
    void createUser_withValidRequest_createsProfileAndSendsEmail() {
        CreateUserRequest request = new CreateUserRequest("new@gm2dev.com", Role.interviewer);

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("new@gm2dev.com");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(true);

        ProfileDto dto = new ProfileDto(mappedProfile.getId(), "new@gm2dev.com", Role.interviewer);

        when(profileRepository.findByEmail("new@gm2dev.com")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromCreateUserRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(profileMapper.toDto(any(Profile.class))).thenReturn(dto);

        ProfileDto result = adminService.createUser(request);

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertEquals("new@gm2dev.com", saved.getEmail());
        assertEquals(Role.interviewer, saved.getRole());
        assertTrue(saved.isEmailVerified());
        assertNotNull(saved.getPasswordHash());
        assertEquals(dto, result);

        verify(emailService).queueTemporaryPasswordEmail(eq("new@gm2dev.com"), anyString());
    }

    @Test
    void createUser_withLcareraDevEmail_succeeds() {
        CreateUserRequest request = new CreateUserRequest("user@lcarera.dev", Role.interviewer);

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("user@lcarera.dev");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(true);

        ProfileDto dto = new ProfileDto(mappedProfile.getId(), "user@lcarera.dev", Role.interviewer);

        when(profileRepository.findByEmail("user@lcarera.dev")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromCreateUserRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(profileMapper.toDto(any(Profile.class))).thenReturn(dto);

        assertDoesNotThrow(() -> adminService.createUser(request));
    }

    @Test
    void createUser_whenTemporaryPasswordEmailFails_throwsRuntimeException() {
        CreateUserRequest request = new CreateUserRequest("new@gm2dev.com", Role.interviewer);

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("new@gm2dev.com");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(true);

        when(profileRepository.findByEmail("new@gm2dev.com")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromCreateUserRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Email delivery failed"))
                .when(emailService).queueTemporaryPasswordEmail(anyString(), anyString());

        assertThrows(RuntimeException.class, () -> adminService.createUser(request));
    }

    @Test
    void createUser_withExistingEmail_throwsIllegalStateException() {
        CreateUserRequest request = new CreateUserRequest("existing@gm2dev.com", Role.interviewer);
        when(profileRepository.findByEmail("existing@gm2dev.com")).thenReturn(Optional.of(new Profile()));
        assertThrows(IllegalStateException.class, () -> adminService.createUser(request));
    }

    @Test
    void createUser_withNonAllowedDomainEmail_throwsSecurityException() {
        CreateUserRequest request = new CreateUserRequest("user@gmail.com", Role.interviewer);
        assertThrows(SecurityException.class, () -> adminService.createUser(request));
    }

    @Test
    void updateRole_withValidId_updatesRole() {
        UUID id = UUID.randomUUID();
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole(Role.interviewer);

        when(profileRepository.findById(id)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.updateRole(id, Role.admin);

        assertEquals(Role.admin, profile.getRole());
        verify(profileRepository).save(profile);
    }

    @Test
    void updateRole_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> adminService.updateRole(id, Role.admin));
    }

    @Test
    void deleteUser_withValidId_deletesProfile() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);
        when(interviewRepository.existsByInterviewerId(id)).thenReturn(false);
        when(interviewRepository.existsByTalentAcquisitionId(id)).thenReturn(false);
        when(shadowingRequestRepository.existsByShadowerId(id)).thenReturn(false);
        adminService.deleteUser(id);
        verify(profileRepository).deleteById(id);
    }

    @Test
    void deleteUser_withExistingInterviewsAsInterviewer_throwsIllegalStateException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);
        when(interviewRepository.existsByInterviewerId(id)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> adminService.deleteUser(id));
        verify(profileRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_withExistingInterviewsAsTalentAcquisition_throwsIllegalStateException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);
        when(interviewRepository.existsByInterviewerId(id)).thenReturn(false);
        when(interviewRepository.existsByTalentAcquisitionId(id)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> adminService.deleteUser(id));
        verify(profileRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_withExistingShadowingRequests_throwsIllegalStateException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);
        when(interviewRepository.existsByInterviewerId(id)).thenReturn(false);
        when(interviewRepository.existsByTalentAcquisitionId(id)).thenReturn(false);
        when(shadowingRequestRepository.existsByShadowerId(id)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> adminService.deleteUser(id));
        verify(profileRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(false);
        assertThrows(EntityNotFoundException.class, () -> adminService.deleteUser(id));
    }

    @Test
    void createUser_generatedPasswordIsShuffled() {
        CreateUserRequest request = new CreateUserRequest("new@gm2dev.com", Role.interviewer);

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("new@gm2dev.com");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(true);

        ProfileDto dto = new ProfileDto(mappedProfile.getId(), "new@gm2dev.com", Role.interviewer);

        when(profileRepository.findByEmail("new@gm2dev.com")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromCreateUserRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(profileMapper.toDto(any(Profile.class))).thenReturn(dto);

        for (int i = 0; i < 20; i++) {
            adminService.createUser(request);
        }

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(20)).queueTemporaryPasswordEmail(eq("new@gm2dev.com"), passwordCaptor.capture());
        List<String> passwords = passwordCaptor.getAllValues();

        boolean allMatchUnshuffledPattern = passwords.stream()
                .allMatch(p -> Character.isUpperCase(p.charAt(0))
                        && Character.isLowerCase(p.charAt(1))
                        && Character.isDigit(p.charAt(2)));
        assertFalse(allMatchUnshuffledPattern,
                "Generated passwords should be shuffled — not always start with [A-Z][a-z][0-9]");
    }
}
