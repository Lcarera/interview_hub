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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShadowingRequestService {

    private final ShadowingRequestRepository shadowingRequestRepository;
    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;

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
    public ShadowingRequest cancelShadowingRequest(UUID id) {
        ShadowingRequest request = findById(id);
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.CANCELLED);
        return shadowingRequestRepository.save(request);
    }

    @Transactional
    public ShadowingRequest approveShadowingRequest(UUID id) {
        ShadowingRequest request = findById(id);
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.APPROVED);
        return shadowingRequestRepository.save(request);
    }

    @Transactional
    public ShadowingRequest rejectShadowingRequest(UUID id, String reason) {
        ShadowingRequest request = findById(id);
        requirePendingStatus(request);

        request.setStatus(ShadowingRequestStatus.REJECTED);
        request.setReason(reason);
        return shadowingRequestRepository.save(request);
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
