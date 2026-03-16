package com.gm2dev.interview_hub.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CloudTasksAuthFilter extends OncePerRequestFilter {

    private final GoogleIdTokenVerifier verifier;
    private final String expectedServiceAccount;

    public CloudTasksAuthFilter(
            @Value("${app.base-url}") String audience,
            CloudTasksProperties cloudTasksProperties
    ) {
        this.expectedServiceAccount = cloudTasksProperties.serviceAccountEmail();
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(audience))
                .build();

        log.info("CloudTasksAuthFilter initialized with audience={}, expectedSA={}",
                audience, expectedServiceAccount);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Rejected /internal request: missing or invalid Authorization header");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing OIDC token");
            return;
        }

        String token = authHeader.substring(7);
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                log.warn("Rejected /internal request: OIDC token verification failed");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid OIDC token");
                return;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();

            if (!expectedServiceAccount.equals(email)) {
                log.warn("Rejected /internal request: unexpected service account email={}, expected={}",
                        email, expectedServiceAccount);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized service account");
                return;
            }

            log.debug("OIDC token validated for service account: {}", email);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("OIDC token verification error", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token verification failed");
        }
    }
}
