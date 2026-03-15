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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    static final DateTimeFormatter EMAIL_DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z")
                    .withZone(ZoneId.of("UTC"));

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

        String meetLink = null;
        try {
            GoogleCalendarService.CalendarEventResult calendarResult = googleCalendarService.createEvent(interview);
            interview.setGoogleEventId(calendarResult.eventId());
            meetLink = calendarResult.meetLink();
            interview = interviewRepository.save(interview);
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event for interview {}: {}", interview.getId(), e.getMessage());
        }

        sendInviteEmails(interview, meetLink);

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

    static String buildSummary(Interview interview) {
        String candidateName = interview.getCandidate() != null && interview.getCandidate().getName() != null
                ? interview.getCandidate().getName() : "Unknown";
        return interview.getTechStack() + " Interview - " + candidateName;
    }

    private void sendInviteEmails(Interview interview, String meetLink) {
        String summary = buildSummary(interview);
        String start = EMAIL_DATE_FMT.format(interview.getStartTime());
        String end = EMAIL_DATE_FMT.format(interview.getEndTime());

        emailService.sendInterviewInviteEmail(interview.getInterviewer().getEmail(), summary, start, end, meetLink);

        if (interview.getCandidate().getEmail() != null) {
            emailService.sendInterviewInviteEmail(interview.getCandidate().getEmail(), summary, start, end, meetLink);
        }

        if (interview.getTalentAcquisition() != null) {
            emailService.sendInterviewInviteEmail(interview.getTalentAcquisition().getEmail(), summary, start, end, meetLink);
        }
    }

    private void sendUpdateEmails(Interview interview) {
        String summary = buildSummary(interview);
        String start = EMAIL_DATE_FMT.format(interview.getStartTime());
        String end = EMAIL_DATE_FMT.format(interview.getEndTime());

        emailService.sendInterviewUpdateEmail(interview.getInterviewer().getEmail(), summary, start, end);

        if (interview.getCandidate().getEmail() != null) {
            emailService.sendInterviewUpdateEmail(interview.getCandidate().getEmail(), summary, start, end);
        }

        if (interview.getTalentAcquisition() != null) {
            emailService.sendInterviewUpdateEmail(interview.getTalentAcquisition().getEmail(), summary, start, end);
        }
    }

    private void sendCancellationEmails(Interview interview) {
        String summary = buildSummary(interview);

        emailService.sendInterviewCancellationEmail(interview.getInterviewer().getEmail(), summary);

        if (interview.getCandidate().getEmail() != null) {
            emailService.sendInterviewCancellationEmail(interview.getCandidate().getEmail(), summary);
        }

        if (interview.getTalentAcquisition() != null) {
            emailService.sendInterviewCancellationEmail(interview.getTalentAcquisition().getEmail(), summary);
        }
    }
}
