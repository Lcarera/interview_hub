package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.dto.LoginRequest;
import com.gm2dev.interview_hub.dto.RegisterRequest;
import com.gm2dev.interview_hub.dto.ResetPasswordRequest;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.gm2dev.interview_hub.config.AllowedDomains.ALLOWED_DOMAINS;

@Service
@Slf4j
public class EmailPasswordAuthService {

    private final ProfileRepository profileRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtProperties jwtProperties;
    private final ProfileMapper profileMapper;
    private final JwtEncoder jwtEncoder;

    public EmailPasswordAuthService(ProfileRepository profileRepository,
                                     VerificationTokenRepository verificationTokenRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmailService emailService,
                                     JwtProperties jwtProperties,
                                     ProfileMapper profileMapper) {
        this.profileRepository = profileRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtProperties = jwtProperties;
        this.profileMapper = profileMapper;

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

        Profile profile = profileMapper.toProfileFromRegisterRequest(request);
        profile.setPasswordHash(passwordEncoder.encode(request.password()));
        profileRepository.save(profile);

        String rawToken = UUID.randomUUID().toString();
        createVerificationToken(profile, TokenType.EMAIL_VERIFICATION, 24, rawToken);
        emailService.sendVerificationEmail(request.email(), rawToken);

        log.debug("Registered new email/password user: {}", request.email());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = hashToken(rawToken);
        VerificationToken vt = verificationTokenRepository.findByTokenAndTokenType(tokenHash, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new SecurityException("Invalid or expired token"));
        if (vt.isUsed() || vt.isExpired()) {
            throw new SecurityException("Invalid or expired token");
        }
        Profile profile = vt.getProfile();
        profile.setEmailVerified(true);
        profileRepository.save(profile);
        vt.setUsed(true);
        verificationTokenRepository.save(vt);
        log.debug("Email verified for: {}", profile.getEmail());
    }

    private static final String LOGIN_FAILURE_MESSAGE = "Invalid credentials";

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Profile profile = profileRepository.findByEmail(request.email())
                .orElse(null);

        if (profile == null) {
            log.debug("Login failed: no account for {}", request.email());
            throw new SecurityException(LOGIN_FAILURE_MESSAGE);
        }

        if (profile.getPasswordHash() == null) {
            log.debug("Login failed: Google-only account {}", request.email());
            throw new SecurityException(LOGIN_FAILURE_MESSAGE);
        }

        if (!profile.isEmailVerified()) {
            log.debug("Login failed: unverified email {}", request.email());
            throw new SecurityException(LOGIN_FAILURE_MESSAGE);
        }

        if (!passwordEncoder.matches(request.password(), profile.getPasswordHash())) {
            log.debug("Login failed: wrong password for {}", request.email());
            throw new SecurityException(LOGIN_FAILURE_MESSAGE);
        }

        String jwt = issueJwt(profile);
        return new AuthResponse(jwt, jwtProperties.getExpirationSeconds(), profile.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        profileRepository.findByEmail(email).ifPresent(profile -> {
            if (profile.getPasswordHash() != null && !profile.isEmailVerified()) {
                invalidateActiveTokens(profile, TokenType.EMAIL_VERIFICATION);
                String rawToken = UUID.randomUUID().toString();
                createVerificationToken(profile, TokenType.EMAIL_VERIFICATION, 24, rawToken);
                emailService.sendVerificationEmail(email, rawToken);
            }
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        profileRepository.findByEmail(email).ifPresent(profile -> {
            if (profile.getPasswordHash() != null) {
                invalidateActiveTokens(profile, TokenType.PASSWORD_RESET);
                String rawToken = UUID.randomUUID().toString();
                createVerificationToken(profile, TokenType.PASSWORD_RESET, 1, rawToken);
                emailService.sendPasswordResetEmail(email, rawToken);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.token());
        VerificationToken vt = verificationTokenRepository.findByTokenAndTokenType(tokenHash, TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new SecurityException("Invalid or expired token"));
        if (vt.isUsed() || vt.isExpired()) {
            throw new SecurityException("Invalid or expired token");
        }
        Profile profile = vt.getProfile();
        profile.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        profileRepository.save(profile);
        invalidateActiveTokens(profile, TokenType.PASSWORD_RESET);
        log.debug("Password reset for: {}", profile.getEmail());
    }

    private void createVerificationToken(Profile profile, TokenType type, int expirationHours, String rawToken) {
        VerificationToken vt = new VerificationToken();
        vt.setId(UUID.randomUUID());
        vt.setProfile(profile);
        vt.setToken(hashToken(rawToken));
        vt.setTokenType(type);
        vt.setExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS));
        vt.setUsed(false);
        vt.setCreatedAt(Instant.now());
        verificationTokenRepository.save(vt);
    }

    private void invalidateActiveTokens(Profile profile, TokenType tokenType) {
        List<VerificationToken> activeTokens = verificationTokenRepository
                .findByProfileAndTokenTypeAndUsedFalse(profile, tokenType);
        for (VerificationToken token : activeTokens) {
            token.setUsed(true);
        }
        verificationTokenRepository.saveAll(activeTokens);
    }

    String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void validateDomain(String email) {
        String domain = email.substring(email.indexOf('@') + 1);
        if (!ALLOWED_DOMAINS.contains(domain)) {
            throw new SecurityException("Registration is restricted to allowed email domains");
        }
    }

    private String issueJwt(Profile profile) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(profile.getId().toString())
                .claim("email", profile.getEmail())
                .claim("role", profile.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getExpirationSeconds()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
