package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.service.dto.UpdateInterviewRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class InterviewServiceTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Test
    void createInterview_withExistingInterviewer_savesInterview() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "test@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        CreateInterviewRequest request = new CreateInterviewRequest(
                profileId,
                "google-event-123",
                Map.of("name", "Candidate"),
                "Java",
                start,
                end
        );

        Interview result = interviewService.createInterview(request);

        assertNotNull(result.getId());
        assertEquals(profileId, result.getInterviewer().getId());
        assertEquals("google-event-123", result.getGoogleEventId());
        assertEquals("Java", result.getTechStack());
        assertEquals(InterviewStatus.SCHEDULED, result.getStatus());
        assertEquals(start, result.getStartTime());
        assertEquals(end, result.getEndTime());
    }

    @Test
    void createInterview_withNonExistentInterviewer_throwsEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        CreateInterviewRequest request = new CreateInterviewRequest(
                nonExistentId,
                "google-event-456",
                Map.of("name", "Candidate"),
                "Python",
                start,
                end
        );

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> interviewService.createInterview(request)
        );
        assertTrue(ex.getMessage().contains(nonExistentId.toString()));
    }

    @Test
    void findAll_returnsAllInterviews() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "all@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        interviewService.createInterview(new CreateInterviewRequest(profileId, null, null, "Java", start, end));
        interviewService.createInterview(new CreateInterviewRequest(profileId, null, null, "Python", start, end));

        Page<Interview> all = interviewService.findAll(Pageable.unpaged());
        assertTrue(all.getTotalElements() >= 2);
    }

    @Test
    void findById_returnsInterview() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "find@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Interview created = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, null, "Go", start, end));

        Interview found = interviewService.findById(created.getId());
        assertEquals(created.getId(), found.getId());
        assertEquals("Go", found.getTechStack());
    }

    @Test
    void findById_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class,
                () -> interviewService.findById(UUID.randomUUID()));
    }

    @Test
    void updateInterview_updatesFields() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "update@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Interview created = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, null, "Java", start, end));

        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);

        UpdateInterviewRequest updateRequest = new UpdateInterviewRequest(
                "new-google-id",
                Map.of("name", "Updated Candidate"),
                "Kotlin",
                newStart,
                newEnd,
                InterviewStatus.SCHEDULED
        );

        Interview updated = interviewService.updateInterview(created.getId(), updateRequest);

        assertEquals("new-google-id", updated.getGoogleEventId());
        assertEquals("Kotlin", updated.getTechStack());
        assertEquals(newStart, updated.getStartTime());
        assertEquals(newEnd, updated.getEndTime());
    }

    @Test
    void updateInterview_withNonExistentId_throwsEntityNotFoundException() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        UpdateInterviewRequest request = new UpdateInterviewRequest(
                null, null, "Java", start, end, InterviewStatus.SCHEDULED);

        assertThrows(EntityNotFoundException.class,
                () -> interviewService.updateInterview(UUID.randomUUID(), request));
    }

    @Test
    void deleteInterview_removesInterview() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "delete@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Interview created = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, null, "Rust", start, end));

        interviewService.deleteInterview(created.getId());

        assertFalse(interviewRepository.findById(created.getId()).isPresent());
    }

    @Test
    void deleteInterview_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class,
                () -> interviewService.deleteInterview(UUID.randomUUID()));
    }
}
