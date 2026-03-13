package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
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
    private final ProfileMapper profileMapper;

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

        Profile profile = profileMapper.toProfileFromCreateUserRequest(request);
        profile.setPasswordHash(passwordEncoder.encode(temporaryPassword));

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
        sb.append(CHARS.charAt(RANDOM.nextInt(24)));       // uppercase
        sb.append(CHARS.charAt(24 + RANDOM.nextInt(24)));  // lowercase
        sb.append(CHARS.charAt(48 + RANDOM.nextInt(8)));   // digit
        for (int i = 3; i < 12; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
