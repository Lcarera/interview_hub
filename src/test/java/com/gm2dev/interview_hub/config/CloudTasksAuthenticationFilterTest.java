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
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudTasksAuthenticationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CloudTasksAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new CloudTasksAuthenticationFilter("test-sa@project.iam.gserviceaccount.com");
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
}
