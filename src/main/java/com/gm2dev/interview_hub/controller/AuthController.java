package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Google OAuth2 authentication endpoints")
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Operation(summary = "Redirect to Google consent screen", description = "Initiates the Google OAuth2 login flow. Redirects the user to Google's consent page.",
            security = {}, responses = {@ApiResponse(responseCode = "302", description = "Redirect to Google")})
    @GetMapping("/auth/google")
    public ResponseEntity<Void> redirectToGoogle() {
        String authUrl = authService.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    @Operation(summary = "Handle Google OAuth callback", description = "Exchanges the authorization code for tokens and redirects to the frontend with a JWT.",
            security = {}, responses = {
                    @ApiResponse(responseCode = "302", description = "Redirect to frontend with token"),
                    @ApiResponse(responseCode = "403", description = "Non-@gm2dev.com account"),
                    @ApiResponse(responseCode = "502", description = "Google token exchange failed")})
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

    @Operation(summary = "Exchange authorization code for token", description = "Postman-compatible endpoint. Accepts code + redirect_uri and returns an access token.",
            security = {}, responses = {
                    @ApiResponse(responseCode = "200", description = "Token issued"),
                    @ApiResponse(responseCode = "403", description = "Non-@gm2dev.com account"),
                    @ApiResponse(responseCode = "502", description = "Google token exchange failed")})
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
