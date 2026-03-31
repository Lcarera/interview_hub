package com.gm2dev.interview_hub.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudTasksAuthenticationFilterTest {

    private static final String EXPECTED_SERVICE_ACCOUNT = "test-sa@project.iam.gserviceaccount.com";
    private static final String EXPECTED_AUDIENCE = "https://app.example.com";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private GoogleIdTokenVerifier tokenVerifier;

    private CloudTasksAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new CloudTasksAuthenticationFilter(EXPECTED_SERVICE_ACCOUNT, EXPECTED_AUDIENCE, tokenVerifier);
    }

    @Nested
    class ConstructorValidation {
        @Test
        void throwsWhenServiceAccountEmailIsNull() {
            assertThatThrownBy(() -> 
                new CloudTasksAuthenticationFilter(null, EXPECTED_AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedServiceAccountEmail must not be null or blank");
        }

        @Test
        void throwsWhenServiceAccountEmailIsBlank() {
            assertThatThrownBy(() -> 
                new CloudTasksAuthenticationFilter("  ", EXPECTED_AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedServiceAccountEmail must not be null or blank");
        }

        @Test
        void throwsWhenAudienceIsNull() {
            assertThatThrownBy(() -> 
                new CloudTasksAuthenticationFilter(EXPECTED_SERVICE_ACCOUNT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedAudience must not be null or blank");
        }

        @Test
        void throwsWhenAudienceIsBlank() {
            assertThatThrownBy(() -> 
                new CloudTasksAuthenticationFilter(EXPECTED_SERVICE_ACCOUNT, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedAudience must not be null or blank");
        }
    }

    @Test
    void shouldNotFilter_whenNotInternalEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/interviews");

        boolean shouldNotFilter = filter.shouldNotFilter(request);

        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    void shouldFilter_whenInternalEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/email-worker");

        boolean shouldNotFilter = filter.shouldNotFilter(request);

        assertThat(shouldNotFilter).isFalse();
    }

    @Test
    void doFilterInternal_rejectsRequestWithoutAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_rejectsRequestWithInvalidAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic sometoken");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_rejectsTokenWithWrongAudience() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-wrong-audience");
        when(tokenVerifier.verify("valid-token-wrong-audience")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid OIDC token");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_rejectsTokenWithWrongServiceAccountEmail() throws Exception {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(tokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn("wrong-sa@other-project.iam.gserviceaccount.com");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Token does not match expected service account");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_rejectsRequestWithoutCloudTasksHeader() throws Exception {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(tokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EXPECTED_SERVICE_ACCOUNT);
        when(request.getHeader("X-CloudTasks-QueueName")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Missing X-CloudTasks-QueueName header");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_authenticatesValidRequest() throws Exception {
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(tokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EXPECTED_SERVICE_ACCOUNT);
        when(request.getHeader("X-CloudTasks-QueueName")).thenReturn("email-queue");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(EXPECTED_SERVICE_ACCOUNT);
    }

    @Test
    void doFilterInternal_handlesVerificationException() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(tokenVerifier.verify("invalid-token"))
                .thenThrow(new GeneralSecurityException("Token verification failed"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Failed to verify OIDC token");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
