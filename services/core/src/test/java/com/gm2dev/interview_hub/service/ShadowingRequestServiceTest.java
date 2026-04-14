package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.client.CalendarServiceClient;
import com.gm2dev.interview_hub.domain.*;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.email.EmailMessage;
import com.gm2dev.interview_hub.repository.CandidateRepository;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.ShadowingRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

    @Autowired
    private CandidateRepository candidateRepository;

    @MockitoBean
    private CalendarServiceClient calendarServiceClient;

    @MockitoBean
    private EmailPublisher emailPublisher;

    private Profile interviewer;
    private Profile shadower;
    private Interview interview;

    @BeforeEach
    void setUp() {
        interviewer = new Profile(UUID.randomUUID(), "interviewer@example.com", Role.interviewer);
        shadower = new Profile(UUID.randomUUID(), "shadower@example.com", Role.interviewer);
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
    void cancelShadowingRequest_whenAlreadyCancelled_throwsIllegalStateException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId()); // now CANCELLED

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
    void rejectShadowingRequest_whenAlreadyRejected_throwsIllegalStateException() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.rejectShadowingRequest(request.getId(), null, interviewer.getId()); // now REJECTED

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

        verify(calendarServiceClient).addAttendee(
                eq("gcal-shadow-event"), any(AttendeeRequest.class));
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

    @Test
    void approveShadowingRequest_withCandidateWithoutName_usesUnknownInEmail() {
        Candidate candidate = candidateRepository.save(
                new Candidate(null, null, "noname@example.com", null, null, null));
        interview.setCandidate(candidate);
        interview = interviewRepository.save(interview);

        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailPublisher).publish(captor.capture());
        EmailMessage.ShadowingApprovedEmailMessage email = (EmailMessage.ShadowingApprovedEmailMessage) captor.getValue();
        assertEquals("shadower@example.com", email.to());
        assertEquals("Java Interview - Unknown", email.summary());
    }

    @Test
    void approveShadowingRequest_withCandidate_usesCandidateNameInEmail() {
        Candidate candidate = candidateRepository.save(
                new Candidate(null, "Jane Doe", "jane@example.com", null, null, null));
        interview.setCandidate(candidate);
        interview = interviewRepository.save(interview);

        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        ArgumentCaptor<EmailMessage> captor2 = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailPublisher).publish(captor2.capture());
        EmailMessage.ShadowingApprovedEmailMessage email2 = (EmailMessage.ShadowingApprovedEmailMessage) captor2.getValue();
        assertEquals("shadower@example.com", email2.to());
        assertEquals("Java Interview - Jane Doe", email2.summary());
    }

    @Test
    void approveShadowingRequest_sendsApprovalEmail() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());

        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        ArgumentCaptor<EmailMessage> captor3 = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailPublisher).publish(captor3.capture());
        EmailMessage.ShadowingApprovedEmailMessage email3 = (EmailMessage.ShadowingApprovedEmailMessage) captor3.getValue();
        assertEquals("shadower@example.com", email3.to());
        assertTrue(email3.summary().contains("Java"));
    }

    @Test
    void cancelShadowingRequest_whenApproved_setsStatusToCancelled() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        ShadowingRequest cancelled = shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId());

        assertEquals(ShadowingRequestStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee() throws Exception {
        interview.setGoogleEventId("gcal-cancel-approved");
        interview = interviewRepository.save(interview);

        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        shadowingRequestService.cancelShadowingRequest(request.getId(), shadower.getId());

        verify(calendarServiceClient).removeAttendee(
                eq("gcal-cancel-approved"), any(AttendeeRequest.class));
    }

    @Test
    void rejectShadowingRequest_whenApproved_setsStatusToRejected() {
        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        ShadowingRequest rejected = shadowingRequestService.rejectShadowingRequest(request.getId(), "No longer needed", interviewer.getId());

        assertEquals(ShadowingRequestStatus.REJECTED, rejected.getStatus());
        assertEquals("No longer needed", rejected.getReason());
    }

    @Test
    void rejectShadowingRequest_whenApprovedWithGoogleEventId_callsRemoveAttendee() throws Exception {
        interview.setGoogleEventId("gcal-reject-approved");
        interview = interviewRepository.save(interview);

        ShadowingRequest request = shadowingRequestService.requestShadowing(interview.getId(), shadower.getId());
        shadowingRequestService.approveShadowingRequest(request.getId(), interviewer.getId());

        shadowingRequestService.rejectShadowingRequest(request.getId(), "reason", interviewer.getId());

        verify(calendarServiceClient).removeAttendee(
                eq("gcal-reject-approved"), any(AttendeeRequest.class));
    }
}
