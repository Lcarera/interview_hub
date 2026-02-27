package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com";
    private static final String REQUIRED_DOMAIN = "gm2dev.com";
    private static final String SCOPES = "openid email profile https://www.googleapis.com/auth/calendar.events";

    private final GoogleOAuthProperties googleProperties;
    private final JwtProperties jwtProperties;
    private final ProfileRepository profileRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final JwtEncoder jwtEncoder;

    public AuthService(GoogleOAuthProperties googleProperties,
                       JwtProperties jwtProperties,
                       ProfileRepository profileRepository,
                       TokenEncryptionService tokenEncryptionService) {
        this.googleProperties = googleProperties;
        this.jwtProperties = jwtProperties;
        this.profileRepository = profileRepository;
        this.tokenEncryptionService = tokenEncryptionService;

        byte[] keyBytes = jwtProperties.getSigningSecret().getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    public String buildAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", googleProperties.getClientId())
                .queryParam("redirect_uri", googleProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("hd", REQUIRED_DOMAIN)
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
        if (!REQUIRED_DOMAIN.equals(hostedDomain)) {
            throw new SecurityException("Access restricted to @" + REQUIRED_DOMAIN + " accounts");
        }

        String googleSub = payload.getSubject();
        String email = payload.getEmail();

        Profile profile = profileRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> {
                    Profile newProfile = new Profile();
                    newProfile.setId(UUID.randomUUID());
                    newProfile.setGoogleSub(googleSub);
                    newProfile.setEmail(email);
                    newProfile.setRole("interviewer");
                    return newProfile;
                });

        profile.setEmail(email);
        profile.setCalendarEmail(email);
        profile.setGoogleAccessToken(tokenEncryptionService.encrypt(tokenResponse.getAccessToken()));
        if (tokenResponse.getRefreshToken() != null) {
            profile.setGoogleRefreshToken(tokenEncryptionService.encrypt(tokenResponse.getRefreshToken()));
        }
        Long expiresInSeconds = tokenResponse.getExpiresInSeconds();
        if (expiresInSeconds != null) {
            profile.setGoogleTokenExpiry(Instant.now().plusSeconds(expiresInSeconds));
        }

        profileRepository.save(profile);

        String jwt = issueJwt(profile);
        return new AuthResponse(jwt, jwtProperties.getExpirationSeconds(), email);
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

    private String issueJwt(Profile profile) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(profile.getId().toString())
                .claim("email", profile.getEmail())
                .claim("role", profile.getRole())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getExpirationSeconds()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
