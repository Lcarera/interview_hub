package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HmacJwtServiceTest {

    private static final String SIGNING_SECRET = "test-signing-secret-that-is-at-least-32-bytes-long";

    private HmacJwtService jwtService;
    private NimbusJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSigningSecret(SIGNING_SECRET);
        jwtProps.setExpirationSeconds(3600);

        byte[] keyBytes = SIGNING_SECRET.getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));

        jwtService = new HmacJwtService(jwtEncoder, jwtProps);
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void issueToken_returnsValidAuthResponse() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@gm2dev.com");
        profile.setRole(Role.interviewer);

        AuthResponse response = jwtService.issueToken(profile);

        assertNotNull(response.token());
        assertEquals(3600, response.expiresIn());
        assertEquals("user@gm2dev.com", response.email());
    }

    @Test
    void issueToken_jwtContainsCorrectClaims() {
        Profile profile = new Profile();
        UUID profileId = UUID.randomUUID();
        profile.setId(profileId);
        profile.setEmail("admin@gm2dev.com");
        profile.setRole(Role.admin);

        AuthResponse response = jwtService.issueToken(profile);
        Jwt jwt = jwtDecoder.decode(response.token());

        assertEquals(profileId.toString(), jwt.getSubject());
        assertEquals("admin@gm2dev.com", jwt.getClaimAsString("email"));
        assertEquals("admin", jwt.getClaimAsString("role"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
    }
}
