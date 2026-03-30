package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

import static com.gm2dev.interview_hub.config.AllowedDomains.ALLOWED_DOMAINS;

@Service
@Slf4j
public class AuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com";
    private static final String SCOPES = "openid email profile";

    private final GoogleOAuthProperties googleProperties;
    private final ProfileRepository profileRepository;
    private final JwtService jwtService;

    public AuthService(GoogleOAuthProperties googleProperties,
                       ProfileRepository profileRepository,
                       JwtService jwtService) {
        this.googleProperties = googleProperties;
        this.profileRepository = profileRepository;
        this.jwtService = jwtService;
    }

    public String buildAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", googleProperties.getClientId())
                .queryParam("redirect_uri", googleProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("hd", "*")
                .build()
                .toUriString();
    }

    @Transactional
    public AuthResponse handleCallback(String code) throws IOException {
        return handleCallback(code, googleProperties.getRedirectUri());
    }

    @Transactional
    public AuthResponse handleCallback(String code, String redirectUri) throws IOException {
        GoogleTokenResponse tokenResponse = exchangeCodeForTokens(code, redirectUri);

        GoogleIdToken idToken = tokenResponse.parseIdToken();
        GoogleIdToken.Payload payload = idToken.getPayload();

        String hostedDomain = payload.getHostedDomain();
        if (hostedDomain == null || !ALLOWED_DOMAINS.contains(hostedDomain)) {
            throw new SecurityException("Access restricted to allowed domain accounts");
        }

        String googleSub = payload.getSubject();
        String email = payload.getEmail();

        Profile profile = profileRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> {
                    Profile newProfile = new Profile();
                    newProfile.setId(UUID.randomUUID());
                    newProfile.setGoogleSub(googleSub);
                    newProfile.setEmail(email);
                    newProfile.setRole(Role.interviewer);
                    return newProfile;
                });

        profile.setEmail(email);

        profileRepository.save(profile);

        return jwtService.issueToken(profile);
    }

    GoogleTokenResponse exchangeCodeForTokens(String code, String redirectUri) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                GOOGLE_TOKEN_URL + "/token",
                googleProperties.getClientId(),
                googleProperties.getClientSecret(),
                code,
                redirectUri
        ).execute();
    }
}
