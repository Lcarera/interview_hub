package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.shared.email.EmailMessage;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.ShadowingRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

import static com.gm2dev.interview_hub.config.AllowedDomains.ALLOWED_DOMAINS;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String CHARS = UPPER + LOWER + DIGITS;

    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailPublisher emailPublisher;
    private final ProfileMapper profileMapper;
    private final InterviewRepository interviewRepository;
    private final ShadowingRequestRepository shadowingRequestRepository;

    @Transactional(readOnly = true)
    public Page<ProfileDto> listUsers(Pageable pageable) {
        return profileRepository.findAll(pageable).map(profileMapper::toDto);
    }

    @Transactional
    public ProfileDto createUser(CreateUserRequest request) {
        String domain = request.email().substring(request.email().indexOf('@') + 1);
        if (!ALLOWED_DOMAINS.contains(domain)) {
            throw new SecurityException("Users must have an allowed email domain");
        }

        if (profileRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("A user with this email already exists");
        }

        String temporaryPassword = generateTemporaryPassword();

        Profile profile = profileMapper.toProfileFromCreateUserRequest(request);
        profile.setPasswordHash(passwordEncoder.encode(temporaryPassword));

        Profile saved = profileRepository.save(profile);
        emailPublisher.publish(new EmailMessage.TemporaryPasswordEmailMessage(request.email(), temporaryPassword));

        log.debug("Admin created user: {}", request.email());
        return profileMapper.toDto(saved);
    }

    @Transactional
    public void updateRole(UUID userId, Role role) {
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
        if (interviewRepository.existsByInterviewerId(userId)
                || interviewRepository.existsByTalentAcquisitionId(userId)) {
            throw new IllegalStateException("Cannot delete user with existing interviews");
        }
        if (shadowingRequestRepository.existsByShadowerId(userId)) {
            throw new IllegalStateException("Cannot delete user with existing shadowing requests");
        }
        profileRepository.deleteById(userId);
        log.debug("Deleted user: {}", userId);
    }

    private String generateTemporaryPassword() {
        char[] password = new char[12];
        password[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        password[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        password[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 3; i < 12; i++) {
            password[i] = CHARS.charAt(RANDOM.nextInt(CHARS.length()));
        }
        // Fisher-Yates shuffle to remove predictable [A-Z][a-z][0-9] prefix
        for (int i = password.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }
        return new String(password);
    }
}
