package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.service.AuthService;
import com.gm2dev.interview_hub.service.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @GetMapping("/auth/google")
    public ResponseEntity<Void> redirectToGoogle() {
        String authUrl = authService.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    @GetMapping("/auth/google/callback")
    public ResponseEntity<AuthResponse> handleCallback(@RequestParam String code) {
        try {
            AuthResponse response = authService.handleCallback(code);
            return ResponseEntity.ok(response);
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
