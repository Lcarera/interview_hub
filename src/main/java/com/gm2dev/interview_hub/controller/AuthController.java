package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/auth/google")
    public ResponseEntity<Void> redirectToGoogle() {
        String authUrl = authService.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    @GetMapping("/auth/google/callback")
    public ResponseEntity<Void> handleCallback(@RequestParam String code) {
        try {
            AuthResponse response = authService.handleCallback(code);
            String encodedEmail = URLEncoder.encode(response.email(), StandardCharsets.UTF_8);
            String location = frontendUrl + "/auth/callback#token=" + response.token()
                    + "&email=" + encodedEmail
                    + "&expiresIn=" + response.expiresIn();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location))
                    .build();
        } catch (SecurityException e) {
            log.warn("Auth rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            log.error("Google OAuth token exchange failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri) {
        try {
            AuthResponse response = authService.handleCallback(code, redirectUri);
            return ResponseEntity.ok(Map.of(
                    "access_token", response.token(),
                    "token_type", "Bearer",
                    "expires_in", response.expiresIn()
            ));
        } catch (SecurityException e) {
            log.warn("Auth rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            log.error("Google OAuth token exchange failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
