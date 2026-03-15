package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import javax.crypto.spec.SecretKeySpec;

import java.time.Instant;
import java.util.List;
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

        byte[] keyBytes = SIGNING_SECRET.getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));

        service = new EmailPasswordAuthService(
                profileRepository, verificationTokenRepository,
                passwordEncoder, emailService, jwtProps, profileMapper, jwtEncoder);
    }

    // --- Register tests ---

    @Test
    void register_withValidGm2devEmail_createsProfileAndSendsVerification() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("user@gm2dev.com");
        mappedProfile.setRole(Role.interviewer);
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
        assertEquals(Role.interviewer, saved.getRole());
        assertFalse(saved.isEmailVerified());

        verify(emailService).sendVerificationEmail(eq("user@gm2dev.com"), anyString());

        // Verify token is stored as SHA-256 hash (not raw UUID)
        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();
        assertEquals(64, savedToken.getToken().length()); // SHA-256 hex is 64 chars
    }

    @Test
    void register_withLcareraDevEmail_succeeds() {
        RegisterRequest request = new RegisterRequest("user@lcarera.dev", "Password1");

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("user@lcarera.dev");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(false);

        when(profileRepository.findByEmail("user@lcarera.dev")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromRegisterRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.register(request));
    }

    @Test
    void register_withNonAllowedDomainEmail_throwsSecurityException() {
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
        String rawToken = "raw-verification-token";
        String hashedToken = service.hashToken(rawToken);

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setEmailVerified(false);

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken(hashedToken);
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());

        when(verificationTokenRepository.findByTokenAndTokenType(hashedToken, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.verifyEmail(rawToken);

        assertTrue(profile.isEmailVerified());
        assertTrue(vt.isUsed());
    }

    @Test
    void verifyEmail_withExpiredToken_throwsSecurityException() {
        String rawToken = "expired-token";
        String hashedToken = service.hashToken(rawToken);

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setToken(hashedToken);
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().minusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now().minusSeconds(7200));

        when(verificationTokenRepository.findByTokenAndTokenType(hashedToken, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));

        assertThrows(SecurityException.class, () -> service.verifyEmail(rawToken));
    }

    @Test
    void verifyEmail_withUsedToken_throwsSecurityException() {
        String rawToken = "used-token";
        String hashedToken = service.hashToken(rawToken);

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setToken(hashedToken);
        vt.setTokenType(TokenType.EMAIL_VERIFICATION);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(true);
        vt.setCreatedAt(Instant.now());

        when(verificationTokenRepository.findByTokenAndTokenType(hashedToken, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(vt));

        assertThrows(SecurityException.class, () -> service.verifyEmail(rawToken));
    }

    // --- Login tests ---

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setRole(Role.interviewer);
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

    // --- Resend verification tests ---

    @Test
    void resendVerification_withUnverifiedPasswordUser_invalidatesOldTokensAndSendsNewEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");
        profile.setEmailVerified(false);

        VerificationToken oldToken = new VerificationToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUsed(false);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
        when(verificationTokenRepository.findByProfileAndTokenTypeAndUsedFalse(profile, TokenType.EMAIL_VERIFICATION))
                .thenReturn(List.of(oldToken));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resendVerification("user@gm2dev.com");

        assertTrue(oldToken.isUsed());
        verify(emailService).sendVerificationEmail(eq("user@gm2dev.com"), anyString());
    }

    @Test
    void resendVerification_withAlreadyVerifiedUser_doesNotSendEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");
        profile.setEmailVerified(true);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

        service.resendVerification("user@gm2dev.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerification_withNonexistentEmail_doesNotThrow() {
        when(profileRepository.findByEmail("nobody@gm2dev.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.resendVerification("nobody@gm2dev.com"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerification_withGoogleOnlyAccount_doesNotSendEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash(null);
        profile.setEmailVerified(false);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

        service.resendVerification("user@gm2dev.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    // --- Forgot password tests ---

    @Test
    void forgotPassword_withExistingPasswordUser_invalidatesOldTokensAndSendsResetEmail() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("hashed");

        VerificationToken oldToken = new VerificationToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUsed(false);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
        when(verificationTokenRepository.findByProfileAndTokenTypeAndUsedFalse(profile, TokenType.PASSWORD_RESET))
                .thenReturn(List.of(oldToken));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.forgotPassword("user@gm2dev.com");

        assertTrue(oldToken.isUsed());
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
    void resetPassword_withValidToken_updatesPasswordAndInvalidatesAllResetTokens() {
        String rawToken = "reset-token-uuid";
        String hashedToken = service.hashToken(rawToken);

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setPasswordHash("old-hash");

        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken(hashedToken);
        vt.setTokenType(TokenType.PASSWORD_RESET);
        vt.setExpiresAt(Instant.now().plusSeconds(3600));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());

        VerificationToken otherToken = new VerificationToken();
        otherToken.setId(UUID.randomUUID());
        otherToken.setUsed(false);

        when(verificationTokenRepository.findByTokenAndTokenType(hashedToken, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(vt));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.findByProfileAndTokenTypeAndUsedFalse(profile, TokenType.PASSWORD_RESET))
                .thenReturn(List.of(vt, otherToken));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword1");
        service.resetPassword(request);

        assertEquals("new-hash", profile.getPasswordHash());
        assertTrue(vt.isUsed());
        assertTrue(otherToken.isUsed());
    }

    @Test
    void resetPassword_withInvalidToken_throwsSecurityException() {
        String rawToken = "invalid";
        String hashedToken = service.hashToken(rawToken);
        when(verificationTokenRepository.findByTokenAndTokenType(hashedToken, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword1");
        assertThrows(SecurityException.class, () -> service.resetPassword(request));
    }

    @Test
    void register_whenVerificationEmailFails_throwsRuntimeException() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");

        Profile mappedProfile = new Profile();
        mappedProfile.setId(UUID.randomUUID());
        mappedProfile.setEmail("user@gm2dev.com");
        mappedProfile.setCalendarEmail("user@gm2dev.com");
        mappedProfile.setRole(Role.interviewer);
        mappedProfile.setEmailVerified(false);

        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.empty());
        when(profileMapper.toProfileFromRegisterRequest(request)).thenReturn(mappedProfile);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Email delivery failed"))
                .when(emailService).sendVerificationEmail(anyString(), anyString());

        assertThrows(RuntimeException.class, () -> service.register(request));
    }

    // --- Token hashing tests ---

    @Test
    void hashToken_producesConsistentSha256Hash() {
        String token = "test-token";
        String hash1 = service.hashToken(token);
        String hash2 = service.hashToken(token);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 hex = 64 chars
    }

    @Test
    void hashToken_differentTokensProduceDifferentHashes() {
        assertNotEquals(service.hashToken("token-a"), service.hashToken("token-b"));
    }
}
