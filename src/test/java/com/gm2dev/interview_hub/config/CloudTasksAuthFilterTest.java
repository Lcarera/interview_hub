package com.gm2dev.interview_hub.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudTasksAuthFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private GoogleIdToken idToken;

    @Mock
    private GoogleIdToken.Payload payload;

    private CloudTasksAuthFilter filter;

    private static final String EXPECTED_SA = "test-sa@project.iam.gserviceaccount.com";
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        CloudTasksProperties props = new CloudTasksProperties(
                "project", "us-central1", "email-queue", true, EXPECTED_SA
        );
        filter = new CloudTasksAuthFilter("https://api.example.com", props);
        filter = spy(filter);
        injectVerifier(filter, verifier);
    }

    private void injectVerifier(CloudTasksAuthFilter filter, GoogleIdTokenVerifier verifier) {
        try {
            var field = CloudTasksAuthFilter.class.getDeclaredField("verifier");
            field.setAccessible(true);
            field.set(filter, verifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void doFilter_nonInternalPath_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/interviews");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }

    @Test
    void doFilter_internalPath_withoutAuthHeader_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(401), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_internalPath_withInvalidAuthHeader_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(401), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_internalPath_withInvalidToken_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(verifier.verify(VALID_TOKEN)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(401), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_internalPath_withWrongServiceAccount_returns403() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(verifier.verify(VALID_TOKEN)).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn("wrong-sa@other.iam.gserviceaccount.com");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(403), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_internalPath_withValidToken_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(verifier.verify(VALID_TOKEN)).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EXPECTED_SA);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void doFilter_internalPath_withVerifierException_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(verifier.verify(VALID_TOKEN)).thenThrow(new RuntimeException("Verification error"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(401), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }
}
