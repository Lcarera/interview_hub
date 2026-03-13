package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.dto.LoginRequest;
import com.gm2dev.interview_hub.dto.RegisterRequest;
import com.gm2dev.interview_hub.dto.ResetPasswordRequest;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailPasswordAuthServiceTest {

    private static final String SIGNING_SECRET = "test-signing-secret-that-is-at-least-32-bytes-long";

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private ProfileMapper profileMapper;

    private EmailPasswordAuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSigningSecret(SIGNING_SECRET);
        jwtProps.setExpirationSeconds(3600);

        service = new EmailPasswordAuthService(
                profileRepository, verificationTokenRepository,
                passwordEncoder, emailService, jwtProps, profileMapper);
    }

    // --- Register tests ---

    @Test
    void register_withValidGm2devEmail_createsProfileAndSendsVerification() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");
        
        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("user@gm2dev.com");
        mappedProfile.setCalendarEmail("user@gm2dev.com");
        mappedProfile.setRole("interviewer");
        mappedProfile.setEmailVerified(false);
        
        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromRegisterRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(request);

        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profileCaptor.capture());
        Profile saved = profileCaptor.getValue();
        assertEquals("user@gm2dev.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals("interviewer", saved.getRole());
        assertFalse(saved.isEmailVerified());

        verify(emailService).sendVerificationEmail(eq("user@gm2dev.com"), anyString());
    }

    @Test
    void register_withNonGm2devEmail_throwsSecurityException() {
        RegisterRequest request = new RegisterRequest("user@gmail.com", "Password1");
        assertThrows(SecurityException.class, () -> service.register(request));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void register_withExistingEmail_throwsIllegalStateException() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");
        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(new Profile()));
        assertThrows(IllegalStateException.class, () -> service.register(request));
    }

    // --- Verify email tests ---

    @Test
    void verifyEmail_withValidToken_marksProfileAsVerified() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setEmailVerified(false);

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken("valid-token");
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());

        when(verificationTokenRepository.findByTokenAndTokenType("valid-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.verifyEmail("valid-token");

        assertTrue(profile.isEmailVerified());
        assertTrue(vt.isUsed());
    }

    @Test
    void verifyEmail_withExpiredToken_throwsSecurityException() {
        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setToken("expired-token");
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().minusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now().minusSeconds(7200));

        when(verificationTokenRepository.findByTokenAndTokenType("expired-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));

        assertThrows(SecurityException.class, () -> service.verifyEmail("expired-token"));
    }

    @Test
    void verifyEmail_withUsedToken_throwsSecurityException() {
        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setToken("used-token");
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(true);
        vt.setCreatedAt(Instant.now());

        when(verificationTokenRepository.findByTokenAndTokenType("used-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));

        assertThrows(SecurityException.class, () -> service.verifyEmail("used-token"));
    }

    // --- Login tests ---

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setRole("interviewer");
        profile.setPasswordHash("hashed");
        profile.setEmailVerified(true);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
        when(passwordEncoder.matches("Password1", "hashed")).thenReturn(true);

        LoginRequest request = new LoginRequest("user@gm2dev.com", "Password1");
        AuthResponse response = service.login(request);

        assertNotNull(response.token());
        assertEquals("user@gm2dev.com", response.email());
    }

    @Test
    void login_withWrongPassword_throwsSecurityException() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");
        profile.setEmailVerified(true);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
        when(passwordEncoder.matches("WrongPass1", "hashed")).thenReturn(false);

        LoginRequest request = new LoginRequest("user@gm2dev.com", "WrongPass1");
        assertThrows(SecurityException.class, () -> service.login(request));
    }

    @Test
    void login_withUnverifiedEmail_throwsSecurityException() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");
        profile.setEmailVerified(false);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

        LoginRequest request = new LoginRequest("user@gm2dev.com", "Password1");
        assertThrows(SecurityException.class, () -> service.login(request));
    }

    @Test
    void login_withGoogleOnlyAccount_throwsSecurityException() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash(null);
        profile.setEmailVerified(true);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

        LoginRequest request = new LoginRequest("user@gm2dev.com", "Password1");
        assertThrows(SecurityException.class, () -> service.login(request));
    }

    @Test
    void login_withNonexistentEmail_throwsSecurityException() {
        when(profileRepository.findByEmail("nobody@gm2dev.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nobody@gm2dev.com", "Password1");
        assertThrows(SecurityException.class, () -> service.login(request));
    }

    // --- Forgot password tests ---

    @Test
    void forgotPassword_withExistingPasswordUser_sendsResetEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.forgotPassword("user@gm2dev.com");

        verify(emailService).sendPasswordResetEmail(eq("user@gm2dev.com"), anyString());
    }

    @Test
    void forgotPassword_withNonexistentEmail_doesNotThrow() {
        when(profileRepository.findByEmail("nobody@gm2dev.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.forgotPassword("nobody@gm2dev.com"));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void forgotPassword_withGoogleOnlyAccount_doesNotSendEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash(null);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

        service.forgotPassword("user@gm2dev.com");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    // --- Reset password tests ---

    @Test
    void resetPassword_withValidToken_updatesPassword() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("old-hash");

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken("reset-token");
        vt.setTokenType(TokenType.PASSWORD_RESET);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());

        when(verificationTokenRepository.findByTokenAndTokenType("reset-token", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(vt));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "NewPassword1");
        service.resetPassword(request);

        assertEquals("new-hash", profile.getPasswordHash());
        assertTrue(vt.isUsed());
    }

    @Test
    void resetPassword_withInvalidToken_throwsSecurityException() {
        when(verificationTokenRepository.findByTokenAndTokenType("invalid", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest("invalid", "NewPassword1");
        assertThrows(SecurityException.class, () -> service.resetPassword(request));
    }
}
