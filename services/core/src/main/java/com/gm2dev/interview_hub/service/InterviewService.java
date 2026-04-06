package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.client.CalendarServiceClient;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.mapper.InterviewMapper;
import com.gm2dev.interview_hub.repository.CandidateRepository;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;
    private final CandidateRepository candidateRepository;
    private final CalendarServiceClient calendarServiceClient;
    private final InterviewMapper interviewMapper;

    @Transactional
    public Interview createInterview(CreateInterviewRequest request) {
        log.debug("Creating interview for interviewer: {}", request.getInterviewerId());

        Profile interviewer = profileRepository.findById(request.getInterviewerId())
                .orElseThrow(() -> new EntityNotFoundException("Interviewer not found: " + request.getInterviewerId()));

        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + request.getCandidateId()));

        Interview interview = new Interview();
        interview.setInterviewer(interviewer);
        interview.setCandidate(candidate);
        interview.setTechStack(request.getTechStack());
        interview.setStartTime(request.getStartTime());
        interview.setEndTime(request.getEndTime());
        interview.setStatus(InterviewStatus.SCHEDULED);

        if (request.getTalentAcquisitionId() != null) {
            Profile ta = profileRepository.findById(request.getTalentAcquisitionId())
                    .orElseThrow(() -> new EntityNotFoundException("Talent acquisition profile not found"));
            interview.setTalentAcquisition(ta);
        }

        interview = interviewRepository.save(interview);

        try {
            CalendarEventResponse calendarResult = calendarServiceClient.createEvent(toCalendarRequest(interview));
            interview.setGoogleEventId(calendarResult.eventId());
            interview = interviewRepository.save(interview);
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event for interview {}: {}", interview.getId(), e.getMessage());
        }

        return interview;
    }

    @Transactional(readOnly = true)
    public Page<Interview> findAll(Pageable pageable) {
        return interviewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Interview findById(UUID id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
    }

    @Transactional
    public Interview updateInterview(UUID id, UpdateInterviewRequest request, UUID requesterId) {
        Interview interview = findById(id);
        if (!interview.getInterviewer().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the interviewer can update this interview");
        }

        interviewMapper.updateFromRequest(request, interview);

        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + request.getCandidateId()));
        interview.setCandidate(candidate);

        if (request.getTalentAcquisitionId() != null) {
            Profile ta = profileRepository.findById(request.getTalentAcquisitionId())
                    .orElseThrow(() -> new EntityNotFoundException("Talent acquisition profile not found"));
            interview.setTalentAcquisition(ta);
        } else {
            interview.setTalentAcquisition(null);
        }

        interview = interviewRepository.save(interview);

        if (interview.getGoogleEventId() != null) {
            try {
                calendarServiceClient.updateEvent(interview.getGoogleEventId(), toCalendarRequest(interview));
            } catch (Exception e) {
                log.warn("Failed to update Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        return interview;
    }

    @Transactional
    public void deleteInterview(UUID id, UUID requesterId) {
        Interview interview = findById(id);
        if (!interview.getInterviewer().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the interviewer can delete this interview");
        }

        if (interview.getGoogleEventId() != null) {
            try {
                calendarServiceClient.deleteEvent(interview.getGoogleEventId());
            } catch (Exception e) {
                log.warn("Failed to delete Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        interviewRepository.delete(interview);
    }

    private CalendarEventRequest toCalendarRequest(Interview interview) {
        Candidate candidate = interview.getCandidate();
        List<String> shadowerEmails = interview.getShadowingRequests() == null ? List.of() :
                interview.getShadowingRequests().stream()
                        .filter(sr -> ShadowingRequestStatus.APPROVED.equals(sr.getStatus()))
                        .map(sr -> sr.getShadower().getEmail())
                        .filter(Objects::nonNull)
                        .toList();
        return new CalendarEventRequest(
                interview.getGoogleEventId(),
                interview.getTechStack(),
                candidate != null ? candidate.getName() : null,
                candidate != null ? candidate.getEmail() : null,
                candidate != null ? candidate.getLinkedinUrl() : null,
                candidate != null ? candidate.getPrimaryArea() : null,
                candidate != null ? candidate.getFeedbackLink() : null,
                interview.getInterviewer().getEmail(),
                shadowerEmails,
                interview.getStartTime(),
                interview.getEndTime()
        );
    }

    static String buildSummary(Interview interview) {
        String candidateName = interview.getCandidate() != null && interview.getCandidate().getName() != null
                ? interview.getCandidate().getName() : "Unknown";
        return interview.getTechStack() + " Interview - " + candidateName;
    }
}
