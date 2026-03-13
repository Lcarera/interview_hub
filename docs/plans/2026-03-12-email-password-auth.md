# Email/Password Authentication Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add email/password login alongside existing Google OAuth, with admin user management, and migrate calendar to domain-wide delegation.

**Architecture:** Extend the existing Profile entity with password_hash and email_verified fields. Add a verification_tokens table for email verification and password reset. Both auth methods issue the same HMAC-SHA256 JWT. Admin system uses role-based access with ROLE_admin. Phase 4 migrates GoogleCalendarService to service account with domain-wide delegation.

**Tech Stack:** Spring Boot 4.0.2, Spring Security, BCrypt, JavaMailSender, Angular 21, Angular Material, PostgreSQL

---

## Phase 1: Email/Password Authentication (Backend)

### Task 1: Database Migration — Add password and verification columns

**Files:**
- Create: `supabase/migrations/005_add_email_password_auth.sql`

**Step 1: Write the migration**

```sql
-- Add password and email verification columns to profiles
ALTER TABLE public.profiles ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE public.profiles ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing Google OAuth profiles as verified
UPDATE public.profiles SET email_verified = TRUE WHERE google_sub IS NOT NULL;

-- Create verification tokens table (used for email verification and password reset)
CREATE TABLE public.verification_tokens (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    token_type VARCHAR(20) NOT NULL, -- 'EMAIL_VERIFICATION' or 'PASSWORD_RESET'
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_tokens_token ON public.verification_tokens(token);
CREATE INDEX idx_verification_tokens_profile_id ON public.verification_tokens(profile_id);
```

**Step 2: Commit**

```bash
git add supabase/migrations/005_add_email_password_auth.sql
git commit -m "feat: add migration for email/password auth columns and verification_tokens table"
```

---

### Task 2: Update Profile entity

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/domain/Profile.java`

**Step 1: Add password_hash and email_verified fields to Profile**

Add these fields after `googleTokenExpiry` (line 45):

```java
@JsonIgnore
@Column(name = "password_hash")
private String passwordHash;

@Column(name = "email_verified", nullable = false)
private boolean emailVerified;
```

**Step 2: Run tests to verify nothing breaks**

Run: `./gradlew test`
Expected: All existing tests PASS (H2 uses `ddl-auto: create-drop` so schema is auto-generated)

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/domain/Profile.java
git commit -m "feat: add passwordHash and emailVerified fields to Profile entity"
```

---

### Task 3: Create VerificationToken entity and repository

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/domain/VerificationToken.java`
- Create: `src/main/java/com/gm2dev/interview_hub/domain/TokenType.java`
- Create: `src/main/java/com/gm2dev/interview_hub/repository/VerificationTokenRepository.java`

**Step 1: Create the TokenType enum**

```java
package com.gm2dev.interview_hub.domain;

public enum TokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
```

**Step 2: Create the VerificationToken entity**

```java
package com.gm2dev.interview_hub.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens", schema = "public")
@Data
@NoArgsConstructor
public class VerificationToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
```

**Step 3: Create the repository**

```java
package com.gm2dev.interview_hub.repository;

import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndTokenType(String token, TokenType tokenType);
}
```

**Step 4: Run tests**

Run: `./gradlew test`
Expected: All existing tests PASS

**Step 5: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/domain/TokenType.java \
        src/main/java/com/gm2dev/interview_hub/domain/VerificationToken.java \
        src/main/java/com/gm2dev/interview_hub/repository/VerificationTokenRepository.java
git commit -m "feat: add VerificationToken entity and repository"
```

---

### Task 4: Add Spring Mail dependency and configuration

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

**Step 1: Add spring-boot-starter-mail to build.gradle**

Add after the `spring-boot-starter-validation` line (around line 41 in dependencies block):

```groovy
// Email
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

**Step 2: Add mail configuration to application.yml**

Add after the `token-encryption-key` line (line 36):

```yaml
  mail:
    from: ${MAIL_FROM:noreply@gm2dev.com}

spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

Note: The `spring:` key already exists at the top of the file. Add the `mail:` block nested under the existing `spring:` key, after the `jpa:` block (around line 25). Add `app.mail.from` under the existing `app:` block.

**Step 3: Add test mail config to application-test.yml**

Add at the end:

```yaml
  mail:
    host: localhost
    port: 2525

app:
  mail:
    from: test@gm2dev.com
```

Note: The `app:` key may need to be merged with the existing one.

**Step 4: Run tests**

Run: `./gradlew test`
Expected: All existing tests PASS

**Step 5: Commit**

```bash
git add build.gradle src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat: add Spring Mail dependency and configuration"
```

---

### Task 5: Create EmailService

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/service/EmailService.java`
- Create: `src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java`

**Step 1: Write the failing test**

```java
package com.gm2dev.interview_hub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@gm2dev.com", "http://localhost:8080");
    }

    @Test
    void sendVerificationEmail_sendsEmailWithCorrectLink() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendVerificationEmail("user@gm2dev.com", "abc-token-123");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_sendsEmailWithCorrectLink() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetEmail("user@gm2dev.com", "reset-token-456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendTemporaryPasswordEmail_sendsEmailWithPassword() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendTemporaryPasswordEmail("user@gm2dev.com", "TempPass123");

        verify(mailSender).send(mimeMessage);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailServiceTest`
Expected: FAIL — `EmailService` does not exist

**Step 3: Write the implementation**

```java
package com.gm2dev.interview_hub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String appBaseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.frontend-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.appBaseUrl = appBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/verify?token=" + token;
        String subject = "Interview Hub — Verify your email";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>Click the link below to verify your email address:</p>"
                + "<p><a href=\"" + link + "\">Verify Email</a></p>"
                + "<p>This link expires in 24 hours.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = appBaseUrl + "/auth/reset-password?token=" + token;
        String subject = "Interview Hub — Reset your password";
        String body = "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + link + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    public void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword) {
        String subject = "Interview Hub — Your account has been created";
        String body = "<h2>Welcome to Interview Hub</h2>"
                + "<p>An admin has created an account for you.</p>"
                + "<p>Your temporary password is: <strong>" + temporaryPassword + "</strong></p>"
                + "<p>Please log in and change your password.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Sent email to {} with subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailServiceTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/EmailService.java \
        src/test/java/com/gm2dev/interview_hub/service/EmailServiceTest.java
git commit -m "feat: add EmailService for verification and password reset emails"
```

---

### Task 6: Create auth DTOs

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/dto/RegisterRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/LoginRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/ForgotPasswordRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/ResetPasswordRequest.java`

**Step 1: Create all four DTOs**

```java
// RegisterRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one number")
        String password
) {}
```

```java
// LoginRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
```

```java
// ForgotPasswordRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {}
```

```java
// ResetPasswordRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one number")
        String newPassword
) {}
```

**Step 2: Run tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/dto/RegisterRequest.java \
        src/main/java/com/gm2dev/interview_hub/dto/LoginRequest.java \
        src/main/java/com/gm2dev/interview_hub/dto/ForgotPasswordRequest.java \
        src/main/java/com/gm2dev/interview_hub/dto/ResetPasswordRequest.java
git commit -m "feat: add DTOs for register, login, forgot-password, and reset-password"
```

---

### Task 7: Add BCrypt PasswordEncoder bean

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/config/SecurityConfig.java`

**Step 1: Add PasswordEncoder bean**

Add this bean method inside `SecurityConfig` class (after `jwtAuthenticationConverter` method, around line 90):

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Add the import at the top:

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
```

**Step 2: Run tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/config/SecurityConfig.java
git commit -m "feat: add BCrypt PasswordEncoder bean to SecurityConfig"
```

---

### Task 8: Implement EmailPasswordAuthService — registration

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/service/EmailPasswordAuthService.java`
- Create: `src/test/java/com/gm2dev/interview_hub/service/EmailPasswordAuthServiceTest.java`

**Step 1: Write the failing tests for registration**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.dto.RegisterRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailPasswordAuthServiceTest {

    private static final String SIGNING_SECRET = "test-signing-secret-that-is-at-least-32-bytes-long";

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private EmailPasswordAuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSigningSecret(SIGNING_SECRET);
        jwtProps.setExpirationSeconds(3600);

        service = new EmailPasswordAuthService(
                profileRepository, verificationTokenRepository,
                passwordEncoder, emailService, jwtProps);
    }

    @Test
    void register_withValidGm2devEmail_createsProfileAndSendsVerification() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");
        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(request);

        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profileCaptor.capture());
        Profile saved = profileCaptor.getValue();
        assertEquals("user@gm2dev.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals("interviewer", saved.getRole());
        assertFalse(saved.isEmailVerified());

        verify(emailService).sendVerificationEmail(eq("user@gm2dev.com"), anyString());
    }

    @Test
    void register_withNonGm2devEmail_throwsSecurityException() {
        RegisterRequest request = new RegisterRequest("user@gmail.com", "Password1");

        assertThrows(SecurityException.class, () -> service.register(request));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void register_withExistingEmail_throwsIllegalStateException() {
        RegisterRequest request = new RegisterRequest("user@gm2dev.com", "Password1");
        when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(new Profile()));

        assertThrows(IllegalStateException.class, () -> service.register(request));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailPasswordAuthServiceTest`
Expected: FAIL — class does not exist

**Step 3: Write the implementation**

```java
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
        // Always return success to prevent email enumeration
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
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailPasswordAuthServiceTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/EmailPasswordAuthService.java \
        src/test/java/com/gm2dev/interview_hub/service/EmailPasswordAuthServiceTest.java
git commit -m "feat: add EmailPasswordAuthService with register, verify, login, forgot/reset password"
```

---

### Task 9: Add more tests to EmailPasswordAuthService

**Files:**
- Modify: `src/test/java/com/gm2dev/interview_hub/service/EmailPasswordAuthServiceTest.java`

**Step 1: Add tests for verify, login, forgot-password, and reset-password**

Add these tests to the existing test class:

```java
@Test
void verifyEmail_withValidToken_marksProfileAsVerified() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setEmailVerified(false);

    VerificationToken vt = new VerificationToken();
    vt.setId(UUID.randomUUID());
    vt.setProfile(profile);
    vt.setToken("valid-token");
    vt.setTokenType(TokenType.EMAIL_VERIFICATION);
    vt.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
    vt.setUsed(false);
    vt.setCreatedAt(java.time.Instant.now());

    when(verificationTokenRepository.findByTokenAndTokenType("valid-token", TokenType.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(vt));
    when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

    service.verifyEmail("valid-token");

    assertTrue(profile.isEmailVerified());
    assertTrue(vt.isUsed());
}

@Test
void verifyEmail_withExpiredToken_throwsSecurityException() {
    VerificationToken vt = new VerificationToken();
    vt.setId(UUID.randomUUID());
    vt.setToken("expired-token");
    vt.setTokenType(TokenType.EMAIL_VERIFICATION);
    vt.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
    vt.setUsed(false);
    vt.setCreatedAt(java.time.Instant.now().minusSeconds(7200));

    when(verificationTokenRepository.findByTokenAndTokenType("expired-token", TokenType.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(vt));

    assertThrows(SecurityException.class, () -> service.verifyEmail("expired-token"));
}

@Test
void verifyEmail_withUsedToken_throwsSecurityException() {
    VerificationToken vt = new VerificationToken();
    vt.setId(UUID.randomUUID());
    vt.setToken("used-token");
    vt.setTokenType(TokenType.EMAIL_VERIFICATION);
    vt.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
    vt.setUsed(true);
    vt.setCreatedAt(java.time.Instant.now());

    when(verificationTokenRepository.findByTokenAndTokenType("used-token", TokenType.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(vt));

    assertThrows(SecurityException.class, () -> service.verifyEmail("used-token"));
}

@Test
void login_withValidCredentials_returnsAuthResponse() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setRole("interviewer");
    profile.setPasswordHash("hashed");
    profile.setEmailVerified(true);

    when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
    when(passwordEncoder.matches("Password1", "hashed")).thenReturn(true);

    com.gm2dev.interview_hub.dto.LoginRequest request =
            new com.gm2dev.interview_hub.dto.LoginRequest("user@gm2dev.com", "Password1");
    AuthResponse response = service.login(request);

    assertNotNull(response.token());
    assertEquals("user@gm2dev.com", response.email());
}

@Test
void login_withWrongPassword_throwsSecurityException() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setPasswordHash("hashed");
    profile.setEmailVerified(true);

    when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
    when(passwordEncoder.matches("WrongPass1", "hashed")).thenReturn(false);

    com.gm2dev.interview_hub.dto.LoginRequest request =
            new com.gm2dev.interview_hub.dto.LoginRequest("user@gm2dev.com", "WrongPass1");
    assertThrows(SecurityException.class, () -> service.login(request));
}

@Test
void login_withUnverifiedEmail_throwsSecurityException() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setPasswordHash("hashed");
    profile.setEmailVerified(false);

    when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

    com.gm2dev.interview_hub.dto.LoginRequest request =
            new com.gm2dev.interview_hub.dto.LoginRequest("user@gm2dev.com", "Password1");
    assertThrows(SecurityException.class, () -> service.login(request));
}

@Test
void login_withGoogleOnlyAccount_throwsSecurityException() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setPasswordHash(null);
    profile.setEmailVerified(true);

    when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));

    com.gm2dev.interview_hub.dto.LoginRequest request =
            new com.gm2dev.interview_hub.dto.LoginRequest("user@gm2dev.com", "Password1");
    assertThrows(SecurityException.class, () -> service.login(request));
}

@Test
void forgotPassword_withExistingPasswordUser_sendsResetEmail() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setPasswordHash("hashed");

    when(profileRepository.findByEmail("user@gm2dev.com")).thenReturn(Optional.of(profile));
    when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

    service.forgotPassword("user@gm2dev.com");

    verify(emailService).sendPasswordResetEmail(eq("user@gm2dev.com"), anyString());
}

@Test
void forgotPassword_withNonexistentEmail_doesNotThrow() {
    when(profileRepository.findByEmail("nobody@gm2dev.com")).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> service.forgotPassword("nobody@gm2dev.com"));
    verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
}

@Test
void resetPassword_withValidToken_updatesPassword() {
    Profile profile = new Profile();
    profile.setId(UUID.randomUUID());
    profile.setEmail("user@gm2dev.com");
    profile.setPasswordHash("old-hash");

    VerificationToken vt = new VerificationToken();
    vt.setId(UUID.randomUUID());
    vt.setProfile(profile);
    vt.setToken("reset-token");
    vt.setTokenType(TokenType.PASSWORD_RESET);
    vt.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
    vt.setUsed(false);
    vt.setCreatedAt(java.time.Instant.now());

    when(verificationTokenRepository.findByTokenAndTokenType("reset-token", TokenType.PASSWORD_RESET))
            .thenReturn(Optional.of(vt));
    when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");
    when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

    com.gm2dev.interview_hub.dto.ResetPasswordRequest request =
            new com.gm2dev.interview_hub.dto.ResetPasswordRequest("reset-token", "NewPassword1");
    service.resetPassword(request);

    assertEquals("new-hash", profile.getPasswordHash());
    assertTrue(vt.isUsed());
}
```

**Step 2: Run tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.EmailPasswordAuthServiceTest`
Expected: All PASS

**Step 3: Commit**

```bash
git add src/test/java/com/gm2dev/interview_hub/service/EmailPasswordAuthServiceTest.java
git commit -m "test: add comprehensive tests for EmailPasswordAuthService"
```

---

### Task 10: Create EmailPasswordAuthController

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/controller/EmailPasswordAuthController.java`
- Create: `src/test/java/com/gm2dev/interview_hub/controller/EmailPasswordAuthControllerTest.java`

**Step 1: Write the failing controller tests**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.dto.AuthResponse;
import com.gm2dev.interview_hub.service.EmailPasswordAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailPasswordAuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class EmailPasswordAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailPasswordAuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void register_withValidRequest_returns201() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com", "password": "Password1"}
                            """))
                .andExpect(status().isCreated());

        verify(authService).register(any());
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "not-an-email", "password": "Password1"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withExistingEmail_returns409() throws Exception {
        doThrow(new IllegalStateException("An account with this email already exists"))
                .when(authService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com", "password": "Password1"}
                            """))
                .andExpect(status().isConflict());
    }

    @Test
    void register_withNonGm2devEmail_returns403() throws Exception {
        doThrow(new SecurityException("Registration restricted"))
                .when(authService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gmail.com", "password": "Password1"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_withValidToken_returns200() throws Exception {
        mockMvc.perform(get("/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk());

        verify(authService).verifyEmail("valid-token");
    }

    @Test
    void verify_withInvalidToken_returns403() throws Exception {
        doThrow(new SecurityException("Invalid or expired token"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/auth/verify").param("token", "bad-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthResponse("jwt-token", 3600, "user@gm2dev.com"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com", "password": "Password1"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("user@gm2dev.com"));
    }

    @Test
    void login_withInvalidCredentials_returns403() throws Exception {
        when(authService.login(any()))
                .thenThrow(new SecurityException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com", "password": "WrongPass1"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPassword_returns200Always() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withValidRequest_returns200() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"token": "reset-token", "newPassword": "NewPassword1"}
                            """))
                .andExpect(status().isOk());

        verify(authService).resetPassword(any());
    }

    @Test
    void authEndpoints_arePublic() throws Exception {
        // All new auth endpoints should be publicly accessible (no JWT needed)
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "user@gm2dev.com", "password": "Password1"}
                            """))
                .andExpect(status().isCreated());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.controller.EmailPasswordAuthControllerTest`
Expected: FAIL — class does not exist

**Step 3: Write the controller**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.*;
import com.gm2dev.interview_hub.service.EmailPasswordAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EmailPasswordAuthController {

    private final EmailPasswordAuthService authService;

    @PostMapping("/auth/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
```

**Step 4: Update SecurityConfig to permit new auth endpoints**

Modify `src/main/java/com/gm2dev/interview_hub/config/SecurityConfig.java` line 44. Change:

```java
.requestMatchers("/auth/google", "/auth/google/callback", "/auth/token").permitAll()
```

To:

```java
.requestMatchers("/auth/**").permitAll()
```

**Step 5: Run tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 6: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/controller/EmailPasswordAuthController.java \
        src/test/java/com/gm2dev/interview_hub/controller/EmailPasswordAuthControllerTest.java \
        src/main/java/com/gm2dev/interview_hub/config/SecurityConfig.java
git commit -m "feat: add EmailPasswordAuthController with register, verify, login, forgot/reset endpoints"
```

---

## Phase 2: Admin System (Backend)

### Task 11: Database Migration — Seed admin user

**Files:**
- Create: `supabase/migrations/006_seed_admin_user.sql`

**Step 1: Write the migration**

```sql
-- Promote luciano.carera@gm2dev.com to admin role
UPDATE public.profiles SET role = 'admin' WHERE email = 'luciano.carera@gm2dev.com';
```

**Step 2: Commit**

```bash
git add supabase/migrations/006_seed_admin_user.sql
git commit -m "feat: seed admin role for luciano.carera@gm2dev.com"
```

---

### Task 12: Create AdminService and AdminController

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/dto/CreateUserRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/dto/UpdateRoleRequest.java`
- Create: `src/main/java/com/gm2dev/interview_hub/service/AdminService.java`
- Create: `src/test/java/com/gm2dev/interview_hub/service/AdminServiceTest.java`

**Step 1: Create DTOs**

```java
// CreateUserRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String role
) {}
```

```java
// UpdateRoleRequest.java
package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleRequest(
        @NotBlank String role
) {}
```

**Step 2: Write the failing tests**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(profileRepository, passwordEncoder, emailService);
    }

    @Test
    void createUser_withValidRequest_createsProfileAndSendsEmail() {
        CreateUserRequest request = new CreateUserRequest("new@gm2dev.com", "interviewer");
        when(profileRepository.findByEmail("new@gm2dev.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.createUser(request);

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertEquals("new@gm2dev.com", saved.getEmail());
        assertEquals("interviewer", saved.getRole());
        assertTrue(saved.isEmailVerified());
        assertNotNull(saved.getPasswordHash());

        verify(emailService).sendTemporaryPasswordEmail(eq("new@gm2dev.com"), anyString());
    }

    @Test
    void createUser_withExistingEmail_throwsIllegalStateException() {
        CreateUserRequest request = new CreateUserRequest("existing@gm2dev.com", "interviewer");
        when(profileRepository.findByEmail("existing@gm2dev.com")).thenReturn(Optional.of(new Profile()));

        assertThrows(IllegalStateException.class, () -> adminService.createUser(request));
    }

    @Test
    void createUser_withNonGm2devEmail_throwsSecurityException() {
        CreateUserRequest request = new CreateUserRequest("user@gmail.com", "interviewer");

        assertThrows(SecurityException.class, () -> adminService.createUser(request));
    }

    @Test
    void updateRole_withValidId_updatesRole() {
        UUID id = UUID.randomUUID();
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole("interviewer");

        when(profileRepository.findById(id)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.updateRole(id, "admin");

        assertEquals("admin", profile.getRole());
        verify(profileRepository).save(profile);
    }

    @Test
    void updateRole_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminService.updateRole(id, "admin"));
    }

    @Test
    void deleteUser_withValidId_deletesProfile() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(true);

        adminService.deleteUser(id);

        verify(profileRepository).deleteById(id);
    }

    @Test
    void deleteUser_withInvalidId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(profileRepository.existsById(id)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> adminService.deleteUser(id));
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.AdminServiceTest`
Expected: FAIL

**Step 4: Write the AdminService**

```java
package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private static final String REQUIRED_DOMAIN = "gm2dev.com";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public Profile createUser(CreateUserRequest request) {
        String domain = request.email().substring(request.email().indexOf('@') + 1);
        if (!REQUIRED_DOMAIN.equals(domain)) {
            throw new SecurityException("Users must have @" + REQUIRED_DOMAIN + " email");
        }

        if (profileRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("A user with this email already exists");
        }

        String temporaryPassword = generateTemporaryPassword();

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(request.email());
        profile.setCalendarEmail(request.email());
        profile.setRole(request.role());
        profile.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        profile.setEmailVerified(true);

        Profile saved = profileRepository.save(profile);
        emailService.sendTemporaryPasswordEmail(request.email(), temporaryPassword);

        log.debug("Admin created user: {}", request.email());
        return saved;
    }

    @Transactional
    public void updateRole(UUID userId, String role) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        profile.setRole(role);
        profileRepository.save(profile);
        log.debug("Updated role for {} to {}", profile.getEmail(), role);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        if (!profileRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        profileRepository.deleteById(userId);
        log.debug("Deleted user: {}", userId);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        // Ensure at least one uppercase, one lowercase, one digit
        sb.append(CHARS.charAt(RANDOM.nextInt(24)));       // uppercase
        sb.append(CHARS.charAt(24 + RANDOM.nextInt(24)));  // lowercase
        sb.append(CHARS.charAt(48 + RANDOM.nextInt(8)));   // digit
        for (int i = 3; i < 12; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
```

**Step 5: Run tests**

Run: `./gradlew test --tests com.gm2dev.interview_hub.service.AdminServiceTest`
Expected: All PASS

**Step 6: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/dto/CreateUserRequest.java \
        src/main/java/com/gm2dev/interview_hub/dto/UpdateRoleRequest.java \
        src/main/java/com/gm2dev/interview_hub/service/AdminService.java \
        src/test/java/com/gm2dev/interview_hub/service/AdminServiceTest.java
git commit -m "feat: add AdminService with createUser, updateRole, deleteUser"
```

---

### Task 13: Create AdminController

**Files:**
- Create: `src/main/java/com/gm2dev/interview_hub/controller/AdminController.java`
- Create: `src/test/java/com/gm2dev/interview_hub/controller/AdminControllerTest.java`

**Step 1: Write the failing tests**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.config.JwtProperties;
import com.gm2dev.interview_hub.config.SecurityConfig;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    void listUsers_asAdmin_returns200() throws Exception {
        when(profileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/users")
                        .with(jwt().jwt(j -> j.claim("role", "admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void listUsers_asInterviewer_returns403() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(jwt().jwt(j -> j.claim("role", "interviewer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("new@gm2dev.com");
        profile.setRole("interviewer");

        when(adminService.createUser(any(CreateUserRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/admin/users")
                        .with(jwt().jwt(j -> j.claim("role", "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "new@gm2dev.com", "role": "interviewer"}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    void updateRole_asAdmin_returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/admin/users/" + userId + "/role")
                        .with(jwt().jwt(j -> j.claim("role", "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"role": "admin"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUser_asAdmin_returns204() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/admin/users/" + userId)
                        .with(jwt().jwt(j -> j.claim("role", "admin"))))
                .andExpect(status().isNoContent());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gm2dev.interview_hub.controller.AdminControllerTest`
Expected: FAIL

**Step 3: Write the AdminController**

```java
package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.UpdateRoleRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Page<Profile>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(profileRepository.findAll(pageable));
    }

    @PostMapping
    public ResponseEntity<Profile> createUser(@Valid @RequestBody CreateUserRequest request) {
        Profile created = adminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        adminService.updateRole(id, request.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Step 4: Run tests**

Run: `./gradlew test`
Expected: All PASS

**Step 5: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/controller/AdminController.java \
        src/test/java/com/gm2dev/interview_hub/controller/AdminControllerTest.java
git commit -m "feat: add AdminController with user CRUD endpoints protected by ROLE_admin"
```

---

## Phase 3: Frontend

### Task 14: Add frontend auth service methods for email/password

**Files:**
- Modify: `frontend/src/app/core/services/auth.service.ts`

**Step 1: Add methods to AuthService**

Add `HttpClient` injection and new methods. The updated file:

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'ih_token';
const EMAIL_KEY = 'ih_email';
const EXPIRES_AT_KEY = 'ih_expires_at';
const PROFILE_ID_KEY = 'ih_profile_id';

export interface AuthResponse {
  token: string;
  expiresIn: number;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  readonly email = signal<string | null>(localStorage.getItem(EMAIL_KEY));
  readonly profileId = signal<string | null>(localStorage.getItem(PROFILE_ID_KEY));

  loginWithGoogle(): void {
    window.location.href = `${environment.apiUrl}/auth/google`;
  }

  /** @deprecated Use loginWithGoogle() instead */
  login(): void {
    this.loginWithGoogle();
  }

  register(email: string, password: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/register`, { email, password });
  }

  loginWithEmail(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, { email, password });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.get<void>(`${environment.apiUrl}/auth/verify`, { params: { token } });
  }

  handleCallback(token: string, email: string, expiresIn: number): void {
    const expiresAt = Date.now() + expiresIn * 1000;
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EMAIL_KEY, email);
    localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt));
    this.email.set(email);

    const sub = this.parseJwtSubject(token);
    if (sub) {
      localStorage.setItem(PROFILE_ID_KEY, sub);
      this.profileId.set(sub);
    }
  }

  handleLoginResponse(response: AuthResponse): void {
    this.handleCallback(response.token, response.email, response.expiresIn);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY));
    return !!token && Date.now() < expiresAt;
  }

  getRole(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.role ?? null;
    } catch {
      return null;
    }
  }

  isAdmin(): boolean {
    return this.getRole() === 'admin';
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    localStorage.removeItem(PROFILE_ID_KEY);
    this.email.set(null);
    this.profileId.set(null);
  }

  private parseJwtSubject(token: string): string | null {
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.sub ?? null;
    } catch {
      return null;
    }
  }
}
```

**Step 2: Run tests**

Run: `cd frontend && bun run test`

**Step 3: Commit**

```bash
git add frontend/src/app/core/services/auth.service.ts
git commit -m "feat: add email/password auth methods and role helpers to frontend AuthService"
```

---

### Task 15: Redesign login page with email/password form

**Files:**
- Modify: `frontend/src/app/features/auth/login/login.ts`
- Modify: `frontend/src/app/features/auth/login/login.html`
- Modify: `frontend/src/app/features/auth/login/login.scss`

**Step 1: Update the component**

```typescript
// login.ts
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatDividerModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  loginWithEmail(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.loginWithEmail(this.email, this.password).subscribe({
      next: (response) => {
        this.auth.handleLoginResponse(response);
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 403
          ? 'Invalid email or password'
          : 'An error occurred. Please try again.');
      },
    });
  }

  loginWithGoogle(): void {
    this.auth.loginWithGoogle();
  }
}
```

**Step 2: Update the template**

```html
<!-- login.html -->
<div class="login-container">
  <div class="login-hero">
    <div class="hero-content">
      <mat-icon class="hero-icon">hub</mat-icon>
      <h1>Interview Hub</h1>
      <p class="hero-tagline">Streamline technical interviews and shadowing requests for your team.</p>
    </div>
  </div>

  <div class="login-panel">
    <mat-card class="login-card">
      <mat-card-header>
        <mat-card-title>Welcome</mat-card-title>
        <mat-card-subtitle>Sign in with your @gm2dev.com account</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (error()) {
          <div class="error-message">{{ error() }}</div>
        }

        <form (ngSubmit)="loginWithEmail()" class="email-form">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Email</mat-label>
            <input matInput type="email" [(ngModel)]="email" name="email" required />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Password</mat-label>
            <input matInput type="password" [(ngModel)]="password" name="password" required />
          </mat-form-field>

          <button mat-flat-button class="login-btn full-width" type="submit" [disabled]="loading()">
            @if (loading()) {
              <mat-spinner diameter="20" />
            } @else {
              Sign in
            }
          </button>
        </form>

        <div class="form-links">
          <a routerLink="/auth/forgot-password">Forgot password?</a>
          <a routerLink="/register">Create account</a>
        </div>

        <mat-divider />

        <button mat-stroked-button class="google-btn full-width" (click)="loginWithGoogle()">
          <mat-icon>login</mat-icon>
          Sign in with Google
        </button>
      </mat-card-content>
    </mat-card>
  </div>
</div>
```

**Step 3: Update styles — add to login.scss**

Add after existing styles:

```scss
.email-form {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-top: 1rem;
}

.full-width {
  width: 100%;
}

.login-btn {
  height: 48px;
  font-size: 1rem;
  font-weight: 500;
  background-color: var(--telus-purple) !important;
  color: var(--telus-white) !important;
}

.form-links {
  display: flex;
  justify-content: space-between;
  margin: 0.75rem 0 1.25rem;
  font-size: 0.85rem;

  a {
    color: var(--telus-purple);
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}

mat-divider {
  margin-bottom: 1.25rem;
}

.google-btn {
  height: 48px;
  font-size: 1rem;
  mat-icon {
    margin-right: 0.5rem;
  }
}

.error-message {
  background: #fdecea;
  color: #b71c1c;
  padding: 0.75rem 1rem;
  border-radius: 4px;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}
```

**Step 4: Run tests**

Run: `cd frontend && bun run test`

**Step 5: Commit**

```bash
git add frontend/src/app/features/auth/login/
git commit -m "feat: redesign login page with email/password form and Google OAuth button"
```

---

### Task 16: Create Register, Verify, ForgotPassword, ResetPassword pages

**Files:**
- Create: `frontend/src/app/features/auth/register/register.ts`
- Create: `frontend/src/app/features/auth/register/register.html`
- Create: `frontend/src/app/features/auth/verify/verify.ts`
- Create: `frontend/src/app/features/auth/forgot-password/forgot-password.ts`
- Create: `frontend/src/app/features/auth/reset-password/reset-password.ts`

**Step 1: Create Register component**

```typescript
// register.ts
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatIconModule, MatProgressSpinnerModule,
  ],
  templateUrl: './register.html',
})
export class RegisterComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  confirmPassword = '';
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  register(): void {
    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }
    if (!this.email.endsWith('@gm2dev.com')) {
      this.error.set('Only @gm2dev.com emails are allowed');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.email, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 409
          ? 'An account with this email already exists'
          : err.status === 403
            ? 'Registration is restricted to @gm2dev.com accounts'
            : 'An error occurred. Please try again.');
      },
    });
  }
}
```

```html
<!-- register.html -->
<div class="auth-page">
  <mat-card class="auth-card">
    <mat-card-header>
      <mat-card-title>Create Account</mat-card-title>
      <mat-card-subtitle>Register with your @gm2dev.com email</mat-card-subtitle>
    </mat-card-header>
    <mat-card-content>
      @if (success()) {
        <div class="success-message">
          <mat-icon>check_circle</mat-icon>
          <p>Registration successful! Check your email for a verification link.</p>
          <a mat-button routerLink="/login">Back to login</a>
        </div>
      } @else {
        @if (error()) {
          <div class="error-message">{{ error() }}</div>
        }
        <form (ngSubmit)="register()" class="auth-form">
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" [(ngModel)]="email" name="email" required />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput type="password" [(ngModel)]="password" name="password" required />
            <mat-hint>Min 8 chars, uppercase, lowercase, and a number</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Confirm Password</mat-label>
            <input matInput type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required />
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="20" /> } @else { Register }
          </button>
        </form>
        <p class="form-link"><a routerLink="/login">Already have an account? Sign in</a></p>
      }
    </mat-card-content>
  </mat-card>
</div>
```

**Step 2: Create Verify component**

```typescript
// verify.ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        @if (loading()) {
          <mat-spinner />
        } @else if (success()) {
          <mat-icon class="status-icon success">check_circle</mat-icon>
          <h2>Email Verified!</h2>
          <p>Your email has been verified. You can now sign in.</p>
          <a mat-flat-button routerLink="/login">Go to Login</a>
        } @else {
          <mat-icon class="status-icon error">error</mat-icon>
          <h2>Verification Failed</h2>
          <p>{{ error() }}</p>
          <a mat-flat-button routerLink="/login">Go to Login</a>
        }
      </mat-card>
    </div>
  `,
})
export class VerifyComponent implements OnInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  success = signal(false);
  error = signal('The verification link is invalid or has expired.');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => { this.loading.set(false); },
    });
  }
}
```

**Step 3: Create ForgotPassword component**

```typescript
// forgot-password.ts
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatIconModule, MatProgressSpinnerModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Forgot Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (sent()) {
            <div class="success-message">
              <mat-icon>check_circle</mat-icon>
              <p>If an account exists with that email, a reset link has been sent.</p>
              <a mat-button routerLink="/login">Back to login</a>
            </div>
          } @else {
            <form (ngSubmit)="submit()" class="auth-form">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput type="email" [(ngModel)]="email" name="email" required />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Send Reset Link }
              </button>
            </form>
            <p class="form-link"><a routerLink="/login">Back to login</a></p>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
})
export class ForgotPasswordComponent {
  private auth = inject(AuthService);

  email = '';
  loading = signal(false);
  sent = signal(false);

  submit(): void {
    this.loading.set(true);
    this.auth.forgotPassword(this.email).subscribe({
      next: () => { this.loading.set(false); this.sent.set(true); },
      error: () => { this.loading.set(false); this.sent.set(true); }, // Always show success
    });
  }
}
```

**Step 4: Create ResetPassword component**

```typescript
// reset-password.ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatIconModule, MatProgressSpinnerModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Reset Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (success()) {
            <div class="success-message">
              <mat-icon>check_circle</mat-icon>
              <p>Password reset successfully!</p>
              <a mat-flat-button routerLink="/login">Go to Login</a>
            </div>
          } @else {
            @if (error()) {
              <div class="error-message">{{ error() }}</div>
            }
            <form (ngSubmit)="submit()" class="auth-form">
              <mat-form-field appearance="outline">
                <mat-label>New Password</mat-label>
                <input matInput type="password" [(ngModel)]="newPassword" name="newPassword" required />
                <mat-hint>Min 8 chars, uppercase, lowercase, and a number</mat-hint>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Confirm Password</mat-label>
                <input matInput type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Reset Password }
              </button>
            </form>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
})
export class ResetPasswordComponent implements OnInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  token = '';
  newPassword = '';
  confirmPassword = '';
  loading = signal(false);
  success = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
  }

  submit(): void {
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => {
        this.loading.set(false);
        this.error.set('The reset link is invalid or has expired.');
      },
    });
  }
}
```

**Step 5: Commit**

```bash
git add frontend/src/app/features/auth/
git commit -m "feat: add Register, Verify, ForgotPassword, ResetPassword frontend pages"
```

---

### Task 17: Update routes and add admin guard

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Create: `frontend/src/app/core/guards/admin.guard.ts`

**Step 1: Create admin guard**

```typescript
// admin.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated() && auth.isAdmin()) return true;
  return router.createUrlTree(['/']);
};
```

**Step 2: Update routes**

```typescript
// app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then(m => m.RegisterComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./features/auth/callback/auth-callback').then(m => m.AuthCallbackComponent),
  },
  {
    path: 'auth/verify',
    loadComponent: () => import('./features/auth/verify/verify').then(m => m.VerifyComponent),
  },
  {
    path: 'auth/forgot-password',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then(m => m.ForgotPasswordComponent),
  },
  {
    path: 'auth/reset-password',
    loadComponent: () => import('./features/auth/reset-password/reset-password').then(m => m.ResetPasswordComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then(m => m.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/dashboard/dashboard').then(m => m.DashboardComponent),
      },
      {
        path: 'interviews',
        loadComponent: () => import('./features/interviews/interview-list/interview-list').then(m => m.InterviewListComponent),
      },
      {
        path: 'interviews/:id',
        loadComponent: () => import('./features/interviews/interview-detail/interview-detail').then(m => m.InterviewDetailComponent),
      },
      {
        path: 'candidates',
        loadComponent: () => import('./features/candidates/candidate-list/candidate-list').then(m => m.CandidateListComponent),
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/user-management/user-management').then(m => m.UserManagementComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
```

**Step 3: Run tests**

Run: `cd frontend && bun run test`

**Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/core/guards/admin.guard.ts
git commit -m "feat: add auth routes, admin guard, and admin user-management route"
```

---

### Task 18: Add Admin link to shell navigation

**Files:**
- Modify: `frontend/src/app/features/shell/shell.ts`

**Step 1: Add admin nav link and isAdmin check**

In `ShellComponent`, add after the existing `email` field:

```typescript
protected readonly isAdmin = computed(() => this.authService.isAdmin());
```

Add `computed` to the import from `@angular/core`.

In the template, add the Admin link inside `<nav class="nav-links">` after the Candidates link:

```html
@if (isAdmin()) {
  <a mat-button routerLink="/admin/users" routerLinkActive="active">Admin</a>
}
```

**Step 2: Run tests**

Run: `cd frontend && bun run test`

**Step 3: Commit**

```bash
git add frontend/src/app/features/shell/shell.ts
git commit -m "feat: add Admin nav link in shell for admin users"
```

---

### Task 19: Create Admin User Management page

**Files:**
- Create: `frontend/src/app/core/services/admin.service.ts`
- Create: `frontend/src/app/features/admin/user-management/user-management.ts`
- Create: `frontend/src/app/features/admin/user-management/user-management.html`
- Create: `frontend/src/app/features/admin/create-user-dialog/create-user-dialog.ts`

**Step 1: Create AdminService**

```typescript
// admin.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Profile } from '../models/profile.model';
import { environment } from '../../../environments/environment';

export interface CreateUserRequest {
  email: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/admin/users`;

  listUsers(page = 0, size = 20): Observable<{ content: Profile[]; totalElements: number }> {
    return this.http.get<{ content: Profile[]; totalElements: number }>(
      this.baseUrl, { params: { page: page.toString(), size: size.toString() } }
    );
  }

  createUser(request: CreateUserRequest): Observable<Profile> {
    return this.http.post<Profile>(this.baseUrl, request);
  }

  updateRole(id: string, role: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}/role`, { role });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
```

**Step 2: Create CreateUserDialog**

```typescript
// create-user-dialog.ts
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';

export interface CreateUserDialogResult {
  email: string;
  role: string;
}

@Component({
  selector: 'app-create-user-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Create User</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Email</mat-label>
        <input matInput type="email" [(ngModel)]="email" name="email" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Role</mat-label>
        <mat-select [(ngModel)]="role" name="role">
          <mat-option value="interviewer">Interviewer</mat-option>
          <mat-option value="admin">Admin</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button (click)="submit()" [disabled]="!email">Create</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`],
})
export class CreateUserDialogComponent {
  private dialogRef = inject(MatDialogRef<CreateUserDialogComponent>);

  email = '';
  role = 'interviewer';

  submit(): void {
    this.dialogRef.close({ email: this.email, role: this.role } as CreateUserDialogResult);
  }
}
```

**Step 3: Create UserManagement page**

```typescript
// user-management.ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { FormsModule } from '@angular/forms';
import { Profile } from '../../../core/models/profile.model';
import { AdminService } from '../../../core/services/admin.service';
import { CreateUserDialogComponent, CreateUserDialogResult } from '../create-user-dialog/create-user-dialog';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatDialogModule, MatSelectModule, MatPaginatorModule,
  ],
  templateUrl: './user-management.html',
})
export class UserManagementComponent implements OnInit {
  private adminService = inject(AdminService);
  private dialog = inject(MatDialog);

  users = signal<Profile[]>([]);
  totalUsers = signal(0);
  displayedColumns = ['email', 'role', 'actions'];
  page = 0;
  size = 20;

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.adminService.listUsers(this.page, this.size).subscribe(res => {
      this.users.set(res.content);
      this.totalUsers.set(res.totalElements);
    });
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.size = event.pageSize;
    this.loadUsers();
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(CreateUserDialogComponent, { width: '400px' });
    ref.afterClosed().subscribe((result: CreateUserDialogResult | undefined) => {
      if (result) {
        this.adminService.createUser(result).subscribe(() => this.loadUsers());
      }
    });
  }

  onRoleChange(user: Profile, newRole: string): void {
    this.adminService.updateRole(user.id, newRole).subscribe(() => this.loadUsers());
  }

  deleteUser(user: Profile): void {
    if (confirm(`Delete user ${user.email}?`)) {
      this.adminService.deleteUser(user.id).subscribe(() => this.loadUsers());
    }
  }
}
```

```html
<!-- user-management.html -->
<div class="admin-container">
  <div class="admin-header">
    <h2>User Management</h2>
    <button mat-flat-button (click)="openCreateDialog()">
      <mat-icon>person_add</mat-icon>
      Create User
    </button>
  </div>

  <table mat-table [dataSource]="users()">
    <ng-container matColumnDef="email">
      <th mat-header-cell *matHeaderCellDef>Email</th>
      <td mat-cell *matCellDef="let user">{{ user.email }}</td>
    </ng-container>

    <ng-container matColumnDef="role">
      <th mat-header-cell *matHeaderCellDef>Role</th>
      <td mat-cell *matCellDef="let user">
        <mat-select [ngModel]="user.role" (ngModelChange)="onRoleChange(user, $event)">
          <mat-option value="interviewer">Interviewer</mat-option>
          <mat-option value="admin">Admin</mat-option>
        </mat-select>
      </td>
    </ng-container>

    <ng-container matColumnDef="actions">
      <th mat-header-cell *matHeaderCellDef>Actions</th>
      <td mat-cell *matCellDef="let user">
        <button mat-icon-button color="warn" (click)="deleteUser(user)">
          <mat-icon>delete</mat-icon>
        </button>
      </td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>

  <mat-paginator
    [length]="totalUsers()"
    [pageSize]="size"
    [pageSizeOptions]="[10, 20, 50]"
    (page)="onPageChange($event)"
  />
</div>
```

**Step 4: Run tests**

Run: `cd frontend && bun run test`

**Step 5: Commit**

```bash
git add frontend/src/app/core/services/admin.service.ts \
        frontend/src/app/features/admin/
git commit -m "feat: add admin user management page with create/update/delete"
```

---

## Phase 4: Google Calendar Domain-Wide Delegation

### Task 20: Update GoogleCalendarService to use service account

**Files:**
- Modify: `src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

**Step 1: Add configuration for service account**

In `application.yml`, add under `app:`:

```yaml
  google-service-account:
    key-json: ${GOOGLE_SERVICE_ACCOUNT_KEY:}
```

**Step 2: Create a config class**

Create `src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java`:

```java
package com.gm2dev.interview_hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.google-service-account")
public class GoogleServiceAccountProperties {
    private String keyJson;
}
```

**Step 3: Rewrite `buildCalendarClient` to use service account with impersonation**

Replace the `buildCalendarClient` method in `GoogleCalendarService.java`:

```java
Calendar buildCalendarClient(Profile interviewer) throws IOException {
    GoogleServiceAccountProperties saProperties = this.serviceAccountProperties;

    if (saProperties.getKeyJson() == null || saProperties.getKeyJson().isBlank()) {
        throw new IOException("Google service account key not configured");
    }

    GoogleCredentials credentials = ServiceAccountCredentials
            .fromStream(new ByteArrayInputStream(saProperties.getKeyJson().getBytes()))
            .createScoped(List.of("https://www.googleapis.com/auth/calendar"))
            .createDelegated(interviewer.getCalendarEmail() != null
                    ? interviewer.getCalendarEmail()
                    : interviewer.getEmail());

    credentials.refreshIfExpired();

    HttpTransport transport;
    try {
        transport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException e) {
        throw new IOException("Failed to create HTTP transport", e);
    }

    return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
            .setApplicationName("Interview Hub")
            .build();
}
```

Update the constructor to inject `GoogleServiceAccountProperties`:

```java
private final GoogleServiceAccountProperties serviceAccountProperties;

public GoogleCalendarService(TokenEncryptionService tokenEncryptionService,
                              ProfileRepository profileRepository,
                              GoogleOAuthProperties googleProperties,
                              GoogleServiceAccountProperties serviceAccountProperties) {
    this.tokenEncryptionService = tokenEncryptionService;
    this.profileRepository = profileRepository;
    this.googleProperties = googleProperties;
    this.serviceAccountProperties = serviceAccountProperties;
}
```

Add imports:

```java
import com.gm2dev.interview_hub.config.GoogleServiceAccountProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
```

Remove the old `UserCredentials`-based imports and token refresh logic.

**Step 4: Add test config**

Add to `application-test.yml`:

```yaml
  google-service-account:
    key-json: ""
```

**Step 5: Run tests**

Run: `./gradlew test`
Expected: All tests PASS (GoogleCalendarService is mocked in all tests)

**Step 6: Commit**

```bash
git add src/main/java/com/gm2dev/interview_hub/service/GoogleCalendarService.java \
        src/main/java/com/gm2dev/interview_hub/config/GoogleServiceAccountProperties.java \
        src/main/resources/application.yml \
        src/test/resources/application-test.yml
git commit -m "feat: migrate GoogleCalendarService to domain-wide delegation via service account"
```

---

### Task 21: Update nginx config for new auth routes

**Files:**
- Modify: `frontend/nginx.conf`

**Step 1: Verify that `/auth/*` routes are already proxied**

Check `frontend/nginx.conf`. The existing config routes `/auth/google` and `/auth/token` to the backend. Update to proxy all `/auth/*` routes:

```nginx
location /auth/ {
    proxy_pass http://app:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

This replaces any existing separate `/auth/google` and `/auth/token` location blocks.

**Step 2: Commit**

```bash
git add frontend/nginx.conf
git commit -m "feat: update nginx to proxy all /auth/ routes and /admin/ routes to backend"
```

---

### Task 22: Update CLAUDE.md documentation

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update relevant sections**

Add new auth endpoints to the Architecture section, document new env vars (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `GOOGLE_SERVICE_ACCOUNT_KEY`), and update the Authentication flow to describe both login methods.

Add migration `005_add_email_password_auth.sql` and `006_seed_admin_user.sql` to the Database Schema Management section.

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with email/password auth and admin system documentation"
```

---

## Summary of new environment variables

| Variable | Purpose | Required |
|----------|---------|----------|
| `MAIL_HOST` | SMTP server host | Yes (for email) |
| `MAIL_PORT` | SMTP server port | Yes (for email) |
| `MAIL_USERNAME` | SMTP username | Yes (for email) |
| `MAIL_PASSWORD` | SMTP password | Yes (for email) |
| `MAIL_FROM` | From email address | No (default: noreply@gm2dev.com) |
| `GOOGLE_SERVICE_ACCOUNT_KEY` | Service account JSON key | Yes (for Phase 4 calendar) |
