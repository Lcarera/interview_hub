package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        GoogleOAuthProperties googleProps = new GoogleOAuthProperties();
        googleProps.setClientId("test-client-id");
        googleProps.setClientSecret("test-client-secret");
        googleProps.setRedirectUri("http://localhost:8080/auth/google/callback");

        authService = spy(new AuthService(googleProps, profileRepository, jwtService));
    }

    @Test
    void buildAuthorizationUrl_containsRequiredParams() {
        String url = authService.buildAuthorizationUrl();

        assertTrue(url.contains("accounts.google.com"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope="));
        assertFalse(url.contains("calendar.events"));
        assertTrue(url.contains("hd="));
        assertFalse(url.contains("access_type=offline"));
    }

    @Test
    void handleCallback_withValidGm2devDomain_returnsAuthResponse() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-123", "user@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("valid-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse expectedResponse = new AuthResponse("test-token", 3600, "user@gm2dev.com");
        when(jwtService.issueToken(any(Profile.class))).thenReturn(expectedResponse);

        AuthResponse response = authService.handleCallback("valid-code");

        assertNotNull(response.token());
        assertEquals("user@gm2dev.com", response.email());
        assertEquals(3600, response.expiresIn());

        verify(jwtService).issueToken(any(Profile.class));
    }

    @Test
    void handleCallback_withNonGm2devDomain_throwsSecurityException() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("otherdomain.com", "sub-456", "user@otherdomain.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("bad-code"), anyString());

        assertThrows(SecurityException.class, () -> authService.handleCallback("bad-code"));
        verify(profileRepository, never()).save(any());
        verify(jwtService, never()).issueToken(any());
    }

    @Test
    void handleCallback_withNullDomain_throwsSecurityException() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse(null, "sub-789", "user@gmail.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("personal-code"), anyString());

        assertThrows(SecurityException.class, () -> authService.handleCallback("personal-code"));
        verify(profileRepository, never()).save(any());
        verify(jwtService, never()).issueToken(any());
    }

    @Test
    void handleCallback_existingProfile_updatesTokens() throws Exception {
        Profile existingProfile = new Profile();
        existingProfile.setId(UUID.randomUUID());
        existingProfile.setGoogleSub("sub-existing");
        existingProfile.setEmail("old@gm2dev.com");
        existingProfile.setRole(Role.interviewer);

        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-existing", "updated@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("returning-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-existing")).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse expectedResponse = new AuthResponse("test-token", 3600, "updated@gm2dev.com");
        when(jwtService.issueToken(any(Profile.class))).thenReturn(expectedResponse);

        AuthResponse response = authService.handleCallback("returning-code");

        assertEquals("updated@gm2dev.com", response.email());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertEquals(existingProfile.getId(), saved.getId());
        assertEquals("updated@gm2dev.com", saved.getEmail());
    }

    @Test
    void handleCallback_newProfile_createsWithRandomUuid() throws Exception {
        GoogleTokenResponse tokenResponse = mockTokenResponse("gm2dev.com", "sub-new", "new@gm2dev.com");
        doReturn(tokenResponse).when(authService).exchangeCodeForTokens(eq("new-code"), anyString());
        when(profileRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse expectedResponse = new AuthResponse("test-token", 3600, "new@gm2dev.com");
        when(jwtService.issueToken(any(Profile.class))).thenReturn(expectedResponse);

        authService.handleCallback("new-code");

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("sub-new", saved.getGoogleSub());
        assertEquals("new@gm2dev.com", saved.getEmail());
        assertEquals(Role.interviewer, saved.getRole());
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

}
