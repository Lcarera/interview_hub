package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.repository.ShadowingRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShadowingRequestService {

    private final ShadowingRequestRepository shadowingRequestRepository;
    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;

    @Transactional
    public ShadowingRequest requestShadowing(UUID interviewId, UUID shadowerId) {
        log.debug("Creating shadowing request for interview: {} by shadower: {}", interviewId, shadowerId);

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + interviewId));

        Profile shadower = profileRepository.findById(shadowerId)
                .orElseThrow(() -> new EntityNotFoundException("Shadower not found: " + shadowerId));

        ShadowingRequest request = new ShadowingRequest();
        request.setInterview(interview);
        request.setShadower(shadower);
        request.setStatus(ShadowingRequestStatus.PENDING);

        return shadowingRequestRepository.save(request);
    }

    @Transactional
    public ShadowingRequest cancelShadowingRequest(UUID id, UUID requesterId) {
        ShadowingRequest request = findById(id);
        if (!request.getShadower().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the shadower can cancel this request");
        }
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.CANCELLED);
        return shadowingRequestRepository.save(request);
    }

    @Transactional
    public ShadowingRequest approveShadowingRequest(UUID id, UUID requesterId) {
        ShadowingRequest request = findById(id);
        if (!request.getInterview().getInterviewer().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the interviewer can approve this request");
        }
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.APPROVED);
        ShadowingRequest saved = shadowingRequestRepository.save(request);

        Interview interview = request.getInterview();
        if (interview.getGoogleEventId() != null) {
            try {
                googleCalendarService.addAttendee(
                        interview.getGoogleEventId(),
                        request.getShadower().getEmail());
            } catch (Exception e) {
                log.warn("Failed to add shadower {} to Calendar event {}: {}",
                        request.getShadower().getEmail(), interview.getGoogleEventId(), e.getMessage());
            }
        }

        String summary = InterviewService.buildSummary(interview);

        emailService.sendShadowingApprovedEmail(
                request.getShadower().getEmail(),
                summary,
                InterviewService.EMAIL_DATE_FMT.format(interview.getStartTime()),
                InterviewService.EMAIL_DATE_FMT.format(interview.getEndTime()));

        return saved;
    }

    @Transactional
    public ShadowingRequest rejectShadowingRequest(UUID id, String reason, UUID requesterId) {
        ShadowingRequest request = findById(id);
        if (!request.getInterview().getInterviewer().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the interviewer can reject this request");
        }
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.REJECTED);
        request.setReason(reason);
        return shadowingRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ShadowingRequest> findByInterviewId(UUID interviewId) {
        return shadowingRequestRepository.findByInterviewId(interviewId);
    }

    @Transactional(readOnly = true)
    public List<ShadowingRequest> findByShadowerId(UUID shadowerId) {
        return shadowingRequestRepository.findByShadowerId(shadowerId);
    }

    private ShadowingRequest findById(UUID id) {
        return shadowingRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shadowing request not found: " + id));
    }

    private void requirePendingStatus(ShadowingRequest request) {
        if (request.getStatus() != ShadowingRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Shadowing request is not in PENDING status. Current status: " + request.getStatus());
        }
    }
}
