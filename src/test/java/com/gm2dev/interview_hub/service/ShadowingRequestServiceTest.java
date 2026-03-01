package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.*;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.ShadowingRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class ShadowingRequestServiceTest {

    @Autowired
    private ShadowingRequestService shadowingRequestService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ShadowingRequestRepository shadowingRequestRepository;

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    private Profile interviewer;
    private Profile shadower;
    private Interview interview;

    @BeforeEach
    void setUp() {
        interviewer = new Profile(UUID.randomUUID(), "interviewer@example.com", "interviewer", null);
        shadower = new Profile(UUID.randomUUID(), "shadower@example.com", "interviewer", null);
        interviewer = profileRepository.save(interviewer);
        shadower = profileRepository.save(shadower);

        interview = new Interview();
        interview.setInterviewer(interviewer);
        interview.setTechStack("Java");
        interview.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        interview.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview = interviewRepository.save(interview);
    }

    @Test
    void requestShadowing_createsRequestInPendingStatus() {
        ShadowingRequest result = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        assertNotNull(result.getId());
        assertEquals(interview.getId(), result.getInterview().getId());
        assertEquals(shadower.getId(), result.getShadower().getId());
        assertEquals(ShadowingRequestStatus.PENDING, result.getStatus());
    }

    @Test
    void requestShadowing_withNonExistentInterview_throwsEntityNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        assertThrows(EntityNotFoundException.class,
                () -> shadowingRequestService.requestShadowing(fakeId, shadower.getId()));
    }

    @Test
    void requestShadowing_withNonExistentShadower_throwsEntityNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        assertThrows(EntityNotFoundException.class,
                () -> shadowingRequestService.requestShadowing(interview.getId(), fakeId));
    }

    @Test
    void cancelShadowingRequest_setStatusToCancelled() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        ShadowingRequest cancelled = shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId());

        assertEquals(ShadowingRequestStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelShadowingRequest_whenNotPending_throwsIllegalStateException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        assertThrows(IllegalStateException.class,
                () -> shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId()));
    }

    @Test
    void approveShadowingRequest_setStatusToApproved() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        ShadowingRequest approved = shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        assertEquals(ShadowingRequestStatus.APPROVED, approved.getStatus());
    }

    @Test
    void approveShadowingRequest_whenNotPending_throwsIllegalStateException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.rejectShadowingRequest(request.getId(), null, interviewer.getId());

        assertThrows(IllegalStateException.class,
                () -> shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId()));
    }

    @Test
    void rejectShadowingRequest_setStatusToRejectedWithReason() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        ShadowingRequest rejected = shadowingRequestService.rejectShadowingRequest(request.getId(), "Full capacity", interviewer.getId());

        assertEquals(ShadowingRequestStatus.REJECTED, rejected.getStatus());
        assertEquals("Full capacity", rejected.getReason());
    }

    @Test
    void rejectShadowingRequest_withNullReason_setStatusToRejected() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        ShadowingRequest rejected = shadowingRequestService.rejectShadowingRequest(request.getId(), null, interviewer.getId());

        assertEquals(ShadowingRequestStatus.REJECTED, rejected.getStatus());
        assertNull(rejected.getReason());
    }

    @Test
    void rejectShadowingRequest_whenNotPending_throwsIllegalStateException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        assertThrows(IllegalStateException.class,
                () -> shadowingRequestService.rejectShadowingRequest(request.getId(), "Too late", interviewer.getId()));
    }

    @Test
    void cancelShadowingRequest_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class,
                () -> shadowingRequestService.cancelShadowingRequest(UUID.randomUUID(), shadower.getId()));
    }

    @Test
    void approveShadowingRequest_withGoogleEventId_invitesShaderToCalendar() throws Exception {
        interview.setGoogleEventId("gcal-shadow-event");
        interview = interviewRepository.save(interview);

        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        verify(googleCalendarService).addAttendee(
                interviewer, "gcal-shadow-event", "shadower@example.com");
    }

    @Test
    void cancelShadowingRequest_byNonShadower_throwsAccessDeniedException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        UUID otherId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> shadowingRequestService.cancelShadowingRequest(request.getId(), otherId));
    }

    @Test
    void approveShadowingRequest_byNonInterviewer_throwsAccessDeniedException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        UUID otherId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> shadowingRequestService.approveShadowingRequest(request.getId(), otherId));
    }

    @Test
    void rejectShadowingRequest_byNonInterviewer_throwsAccessDeniedException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        UUID otherId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> shadowingRequestService.rejectShadowingRequest(request.getId(), "reason", otherId));
    }
}
