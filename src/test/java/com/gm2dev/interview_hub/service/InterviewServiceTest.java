package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    @Test
    void createInterview_withExistingInterviewer_savesInterview() {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "test@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        CreateInterviewRequest request = new CreateInterviewRequest(
                profileId,
                Map.of("name", "Candidate"),
                "Java",
                start,
                end
        );

        Interview result = interviewService.createInterview(request);

        assertNotNull(result.getId());
        assertEquals(profileId, result.getInterviewer().getId());
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

        interviewService.createInterview(new CreateInterviewRequest(profileId, null, "Java", start, end));
        interviewService.createInterview(new CreateInterviewRequest(profileId, null, "Python", start, end));

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
                new CreateInterviewRequest(profileId, null, "Go", start, end));

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
                new CreateInterviewRequest(profileId, null, "Java", start, end));

        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);

        UpdateInterviewRequest updateRequest = new UpdateInterviewRequest(
                Map.of("name", "Updated Candidate"),
                "Kotlin",
                newStart,
                newEnd,
                InterviewStatus.SCHEDULED
        );

        Interview updated = interviewService.updateInterview(created.getId(), updateRequest);

        assertEquals("Kotlin", updated.getTechStack());
        assertEquals(newStart, updated.getStartTime());
        assertEquals(newEnd, updated.getEndTime());
    }

    @Test
    void updateInterview_withNonExistentId_throwsEntityNotFoundException() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        UpdateInterviewRequest request = new UpdateInterviewRequest(
                null, "Java", start, end, InterviewStatus.SCHEDULED);

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
                new CreateInterviewRequest(profileId, null, "Rust", start, end));

        interviewService.deleteInterview(created.getId());

        assertFalse(interviewRepository.findById(created.getId()).isPresent());
    }

    @Test
    void deleteInterview_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class,
                () -> interviewService.deleteInterview(UUID.randomUUID()));
    }

    @Test
    void createInterview_calendarSuccess_setsGoogleEventId() throws Exception {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "cal@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        when(googleCalendarService.createEvent(any(Profile.class), any(Interview.class)))
                .thenReturn("gcal-event-123");

        Interview result = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, "Java", start, end));

        assertEquals("gcal-event-123", result.getGoogleEventId());
    }

    @Test
    void createInterview_calendarFailure_stillCreatesInterview() throws Exception {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "calfail@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        when(googleCalendarService.createEvent(any(Profile.class), any(Interview.class)))
                .thenThrow(new RuntimeException("Calendar unavailable"));

        Interview result = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, "Java", start, end));

        assertNotNull(result.getId());
        assertNull(result.getGoogleEventId());
    }

    @Test
    void updateInterview_withGoogleEventId_callsCalendarUpdate() throws Exception {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "upd-cal@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        when(googleCalendarService.createEvent(any(Profile.class), any(Interview.class)))
                .thenReturn("gcal-upd-event");

        Interview created = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, "Java", start, end));

        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);

        interviewService.updateInterview(created.getId(), new UpdateInterviewRequest(
                null, "Kotlin", newStart, newEnd, InterviewStatus.SCHEDULED));

        verify(googleCalendarService).updateEvent(any(Profile.class), any(Interview.class));
    }

    @Test
    void deleteInterview_withGoogleEventId_callsCalendarDelete() throws Exception {
        UUID profileId = UUID.randomUUID();
        Profile interviewer = new Profile(profileId, "del-cal@example.com", "interviewer", null);
        profileRepository.save(interviewer);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        when(googleCalendarService.createEvent(any(Profile.class), any(Interview.class)))
                .thenReturn("gcal-del-event");

        Interview created = interviewService.createInterview(
                new CreateInterviewRequest(profileId, null, "Rust", start, end));

        interviewService.deleteInterview(created.getId());

        verify(googleCalendarService).deleteEvent(any(Profile.class), eq("gcal-del-event"));
    }
}
