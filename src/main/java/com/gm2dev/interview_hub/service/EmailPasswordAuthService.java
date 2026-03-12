package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.dto.LoginRequest;
import com.gm2dev.interview_hub.dto.RegisterRequest;
import com.gm2dev.interview_hub.dto.ResetPasswordRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.VerificationTokenRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
public class EmailPasswordAuthService {

    private static final String REQUIRED_DOMAIN = "gm2dev.com";

    private final ProfileRepository profileRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;

    public EmailPasswordAuthService(ProfileRepository profileRepository,
                                     VerificationTokenRepository verificationTokenRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmailService emailService,
                                     JwtProperties jwtProperties) {
        this.profileRepository = profileRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtProperties = jwtProperties;

        byte[] keyBytes = jwtProperties.getSigningSecret().getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Transactional
    public void register(RegisterRequest request) {
        validateDomain(request.email());

        if (profileRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("An account with this email already exists");
        }

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(request.email());
        profile.setCalendarEmail(request.email());
        profile.setRole("interviewer");
        profile.setPasswordHash(passwordEncoder.encode(request.password()));
        profile.setEmailVerified(false);
        profileRepository.save(profile);

        String token = createVerificationToken(profile, TokenType.EMAIL_VERIFICATION, 24);
        emailService.sendVerificationEmail(request.email(), token);

        log.debug("Registered new email/password user: {}", request.email());
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken vt = findValidToken(token, TokenType.EMAIL_VERIFICATION);
        Profile profile = vt.getProfile();
        profile.setEmailVerified(true);
        profileRepository.save(profile);
        vt.setUsed(true);
        verificationTokenRepository.save(vt);
        log.debug("Email verified for: {}", profile.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Profile profile = profileRepository.findByEmail(request.email())
                .orElseThrow(() -> new SecurityException("Invalid email or password"));

        if (profile.getPasswordHash() == null) {
            throw new SecurityException("This account uses Google login. Please sign in with Google.");
        }

        if (!profile.isEmailVerified()) {
            throw new SecurityException("Please verify your email before logging in");
        }

        if (!passwordEncoder.matches(request.password(), profile.getPasswordHash())) {
            throw new SecurityException("Invalid email or password");
        }

        String jwt = issueJwt(profile);
        return new AuthResponse(jwt, jwtProperties.getExpirationSeconds(), profile.getEmail());
    }

    @Transactional
    public void forgotPassword(String email) {
        profileRepository.findByEmail(email).ifPresent(profile -> {
            if (profile.getPasswordHash() != null) {
                String token = createVerificationToken(profile, TokenType.PASSWORD_RESET, 1);
                emailService.sendPasswordResetEmail(email, token);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationToken vt = findValidToken(request.token(), TokenType.PASSWORD_RESET);
        Profile profile = vt.getProfile();
        profile.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        profileRepository.save(profile);
        vt.setUsed(true);
        verificationTokenRepository.save(vt);
        log.debug("Password reset for: {}", profile.getEmail());
    }

    private String createVerificationToken(Profile profile, TokenType type, int expirationHours) {
        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken(UUID.randomUUID().toString());
        vt.setTokenType(type);
        vt.setExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());
        verificationTokenRepository.save(vt);
        return vt.getToken();
    }

    private VerificationToken findValidToken(String token, TokenType expectedType) {
        VerificationToken vt = verificationTokenRepository.findByTokenAndTokenType(token, expectedType)
                .orElseThrow(() -> new SecurityException("Invalid or expired token"));
        if (vt.isUsed() || vt.isExpired()) {
            throw new SecurityException("Invalid or expired token");
        }
        return vt;
    }

    private void validateDomain(String email) {
        String domain = email.substring(email.indexOf('@') + 1);
        if (!REQUIRED_DOMAIN.equals(domain)) {
            throw new SecurityException("Registration is restricted to @" + REQUIRED_DOMAIN + " accounts");
        }
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
