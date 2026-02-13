package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.dto.AuthResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SIGNING_SECRET = "test-signing-secret-that-is-at-least-32-bytes-long";
    private static final String ENCRYPTION_KEY = "test-encryption-key-32chars-long!";

    @Mock
    private ProfileRepository profileRepository;

    private TokenEncryptionService tokenEncryptionService;
    private AuthService authService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        GoogleOAuthProperties googleProps = new GoogleOAuthProperties();
        googleProps.setClientId("test-client-id");
        googleProps.setClientSecret("test-client-secret");
        googleProps.setRedirectUri("http://localhost:8080/auth/google/callback");

        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSigningSecret(SIGNING_SECRET);
        jwtProps.setExpirationSeconds(3600);

        tokenEncryptionService = new TokenEncryptionService(ENCRYPTION_KEY);

        authService = spy(new AuthService(googleProps, jwtProps, profileRepository, tokenEncryptionService));

        byte[] keyBytes = SIGNING_SECRET.getBytes();
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void buildAuthorizationUrl_containsRequiredParams() {
        String url = authService.buildAuthorizationUrl();

        assertTrue(url.contains("accounts.google.com"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("calendar.events"));
        assertTrue(url.contains("hd=gm2dev.com"));
        assertTrue(url.contains("access_type=offline"));
    }

    @Test
    void handleCallback_withValidGm2devDomain_returnsAuthResponse() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-123", "user@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("valid-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.handleCallback("valid-code");

        assertNotNull(response.token());
        assertEquals("user@gm2dev.com", response.email());
        assertEquals(3600, response.expiresIn());

        // Verify JWT contains correct claims
        Jwt jwt = jwtDecoder.decode(response.token());
        assertEquals("user@gm2dev.com", jwt.getClaimAsString("email"));
        assertEquals("interviewer", jwt.getClaimAsString("role"));
        assertNotNull(jwt.getSubject());
    }

    @Test
    void handleCallback_withNonGm2devDomain_throwsSecurityException() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("otherdomain.com", "sub-456", "user@otherdomain.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("bad-code"), anyString());

        assertThrows(SecurityException.class, () -> authService.handleCallback("bad-code"));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void handleCallback_withNullDomain_throwsSecurityException() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse(null, "sub-789", "user@gmail.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("personal-code"), anyString());

        assertThrows(SecurityException.class, () -> authService.handleCallback("personal-code"));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void handleCallback_existingProfile_updatesTokens() throws Exception {
        Profile existingProfile = new Profile();
        existingProfile.setId(UUID.randomUUID());
        existingProfile.setGoogleSub("sub-existing");
        existingProfile.setEmail("old@gm2dev.com");
        existingProfile.setRole("interviewer");

        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-existing", "updated@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("returning-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-existing")).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.handleCallback("returning-code");

        assertEquals("updated@gm2dev.com", response.email());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertEquals(existingProfile.getId(), saved.getId());
        assertEquals("updated@gm2dev.com", saved.getEmail());
        assertEquals("updated@gm2dev.com", saved.getCalendarEmail());
        assertNotNull(saved.getGoogleAccessToken());
        assertNotNull(saved.getGoogleRefreshToken());
    }

    @Test
    void handleCallback_newProfile_createsWithRandomUuid() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-new", "new@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("new-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.handleCallback("new-code");

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("sub-new", saved.getGoogleSub());
        assertEquals("new@gm2dev.com", saved.getEmail());
        assertEquals("interviewer", saved.getRole());
        assertEquals("new@gm2dev.com", saved.getCalendarEmail());
    }

    @Test
    void handleCallback_withNullRefreshToken_doesNotOverwriteExistingRefreshToken() throws Exception {
        Profile existingProfile = new Profile();
        existingProfile.setId(UUID.randomUUID());
        existingProfile.setGoogleSub("sub-norefresh");
        existingProfile.setEmail("user@gm2dev.com");
        existingProfile.setRole("interviewer");
        existingProfile.setGoogleRefreshToken(tokenEncryptionService.encrypt("old-refresh-token"));

        GoogleTokenResponse tokenResponse = mockTokenResponseWithNulls("gm2dev.com", "sub-norefresh", "user@gm2dev.com", null, null);
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("no-refresh-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-norefresh")).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.handleCallback("no-refresh-code");

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        // Refresh token should not be overwritten when null
        assertEquals("old-refresh-token", tokenEncryptionService.decrypt(saved.getGoogleRefreshToken()));
    }

    @Test
    void handleCallback_tokensAreEncrypted() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-enc", "enc@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("enc-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-enc")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.handleCallback("enc-code");

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();

        // Tokens should be encrypted (not stored as plain text)
        assertNotEquals("mock-access-token", saved.getGoogleAccessToken());
        assertNotEquals("mock-refresh-token", saved.getGoogleRefreshToken());

        // But should be decryptable back to original values
        assertEquals("mock-access-token", tokenEncryptionService.decrypt(saved.getGoogleAccessToken()));
        assertEquals("mock-refresh-token", tokenEncryptionService.decrypt(saved.getGoogleRefreshToken()));
    }

    private GoogleTokenResponse mockTokenResponse(String hostedDomain, String subject, String email) throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setHostedDomain(hostedDomain);
        payload.setSubject(subject);
        payload.setEmail(email);

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);

        GoogleTokenResponse tokenResponse = mock(GoogleTokenResponse.class);
        when(tokenResponse.parseIdToken()).thenReturn(idToken);
        lenient().when(tokenResponse.getAccessToken()).thenReturn("mock-access-token");
        lenient().when(tokenResponse.getRefreshToken()).thenReturn("mock-refresh-token");
        lenient().when(tokenResponse.getExpiresInSeconds()).thenReturn(3600L);

        return tokenResponse;
    }

    private GoogleTokenResponse mockTokenResponseWithNulls(String hostedDomain, String subject, String email,
                                                            String refreshToken, Long expiresInSeconds) throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setHostedDomain(hostedDomain);
        payload.setSubject(subject);
        payload.setEmail(email);

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);

        GoogleTokenResponse tokenResponse = mock(GoogleTokenResponse.class);
        when(tokenResponse.parseIdToken()).thenReturn(idToken);
        lenient().when(tokenResponse.getAccessToken()).thenReturn("mock-access-token");
        lenient().when(tokenResponse.getRefreshToken()).thenReturn(refreshToken);
        lenient().when(tokenResponse.getExpiresInSeconds()).thenReturn(expiresInSeconds);

        return tokenResponse;
    }
}
