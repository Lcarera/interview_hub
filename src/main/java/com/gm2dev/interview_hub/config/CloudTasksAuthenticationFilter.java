package com.gm2dev.interview_hub.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
public class CloudTasksAuthenticationFilter extends OncePerRequestFilter {

    private final GoogleIdTokenVerifier tokenVerifier;
    private final String expectedServiceAccountEmail;

    public CloudTasksAuthenticationFilter(String expectedServiceAccountEmail) {
        this.expectedServiceAccountEmail = expectedServiceAccountEmail;
        this.tokenVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .build();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for Cloud Tasks endpoint");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            var idToken = tokenVerifier.verify(token);
            
            if (idToken == null) {
                log.warn("Invalid OIDC token for Cloud Tasks endpoint");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid OIDC token");
                return;
            }

            String email = idToken.getPayload().getEmail();
            if (expectedServiceAccountEmail != null && !expectedServiceAccountEmail.equals(email)) {
                log.warn("OIDC token email {} does not match expected service account {}", 
                        email, expectedServiceAccountEmail);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, 
                        "Token does not match expected service account");
                return;
            }

            // Secondary validation: check for Cloud Tasks header
            String queueName = request.getHeader("X-CloudTasks-QueueName");
            if (queueName == null || queueName.isBlank()) {
                log.warn("Missing X-CloudTasks-QueueName header");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, 
                        "Missing X-CloudTasks-QueueName header");
                return;
            }

            // Authentication successful - set security context
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_cloudtasks"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Failed to verify OIDC token", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Failed to verify OIDC token");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }
}
