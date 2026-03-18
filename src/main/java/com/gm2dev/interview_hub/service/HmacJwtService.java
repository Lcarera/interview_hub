package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.AuthResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HmacJwtService implements JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public HmacJwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public AuthResponse issueToken(Profile profile) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(profile.getId().toString())
                .claim("email", profile.getEmail())
                .claim("role", profile.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getExpirationSeconds()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, jwtProperties.getExpirationSeconds(), profile.getEmail());
    }
}
