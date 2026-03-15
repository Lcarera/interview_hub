package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.mapper.InterviewMapper;
import com.gm2dev.interview_hub.repository.CandidateRepository;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;
    private final CandidateRepository candidateRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;
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
            String googleEventId = googleCalendarService.createEvent(interview);
            interview.setGoogleEventId(googleEventId);
            interview = interviewRepository.save(interview);
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event for interview {}: {}", interview.getId(), e.getMessage());
        }

        sendInviteEmails(interview);

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
                googleCalendarService.updateEvent(interview);
            } catch (Exception e) {
                log.warn("Failed to update Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        sendUpdateEmails(interview);

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
                googleCalendarService.deleteEvent(interview.getGoogleEventId());
            } catch (Exception e) {
                log.warn("Failed to delete Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        sendCancellationEmails(interview);

        interviewRepository.delete(interview);
    }

    private String buildSummary(Interview interview) {
        return interview.getTechStack() + " Interview - "
                + (interview.getCandidate() != null && interview.getCandidate().getName() != null
                   ? interview.getCandidate().getName() : "Unknown");
    }

    private void sendInviteEmails(Interview interview) {
        String summary = buildSummary(interview);
        String start = interview.getStartTime().toString();
        String end = interview.getEndTime().toString();
        String meetLink = null;

        try {
            emailService.sendInterviewInviteEmail(interview.getInterviewer().getEmail(), summary, start, end, meetLink);
        } catch (Exception e) {
            log.warn("Failed to send invite email to interviewer {}: {}", interview.getInterviewer().getEmail(), e.getMessage());
        }

        if (interview.getCandidate() != null && interview.getCandidate().getEmail() != null) {
            try {
                emailService.sendInterviewInviteEmail(interview.getCandidate().getEmail(), summary, start, end, meetLink);
            } catch (Exception e) {
                log.warn("Failed to send invite email to candidate {}: {}", interview.getCandidate().getEmail(), e.getMessage());
            }
        }

        if (interview.getTalentAcquisition() != null) {
            try {
                emailService.sendInterviewInviteEmail(interview.getTalentAcquisition().getEmail(), summary, start, end, meetLink);
            } catch (Exception e) {
                log.warn("Failed to send invite email to talent acquisition {}: {}", interview.getTalentAcquisition().getEmail(), e.getMessage());
            }
        }
    }

    private void sendUpdateEmails(Interview interview) {
        String summary = buildSummary(interview);
        String start = interview.getStartTime().toString();
        String end = interview.getEndTime().toString();

        try {
            emailService.sendInterviewUpdateEmail(interview.getInterviewer().getEmail(), summary, start, end);
        } catch (Exception e) {
            log.warn("Failed to send update email to interviewer {}: {}", interview.getInterviewer().getEmail(), e.getMessage());
        }

        if (interview.getCandidate() != null && interview.getCandidate().getEmail() != null) {
            try {
                emailService.sendInterviewUpdateEmail(interview.getCandidate().getEmail(), summary, start, end);
            } catch (Exception e) {
                log.warn("Failed to send update email to candidate {}: {}", interview.getCandidate().getEmail(), e.getMessage());
            }
        }

        if (interview.getTalentAcquisition() != null) {
            try {
                emailService.sendInterviewUpdateEmail(interview.getTalentAcquisition().getEmail(), summary, start, end);
            } catch (Exception e) {
                log.warn("Failed to send update email to talent acquisition {}: {}", interview.getTalentAcquisition().getEmail(), e.getMessage());
            }
        }
    }

    private void sendCancellationEmails(Interview interview) {
        String summary = buildSummary(interview);

        try {
            emailService.sendInterviewCancellationEmail(interview.getInterviewer().getEmail(), summary);
        } catch (Exception e) {
            log.warn("Failed to send cancellation email to interviewer {}: {}", interview.getInterviewer().getEmail(), e.getMessage());
        }

        if (interview.getCandidate() != null && interview.getCandidate().getEmail() != null) {
            try {
                emailService.sendInterviewCancellationEmail(interview.getCandidate().getEmail(), summary);
            } catch (Exception e) {
                log.warn("Failed to send cancellation email to candidate {}: {}", interview.getCandidate().getEmail(), e.getMessage());
            }
        }

        if (interview.getTalentAcquisition() != null) {
            try {
                emailService.sendInterviewCancellationEmail(interview.getTalentAcquisition().getEmail(), summary);
            } catch (Exception e) {
                log.warn("Failed to send cancellation email to talent acquisition {}: {}", interview.getTalentAcquisition().getEmail(), e.getMessage());
            }
        }
    }
}
