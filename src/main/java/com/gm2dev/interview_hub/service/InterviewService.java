package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public Interview createInterview(CreateInterviewRequest request) {
        log.debug("Creating interview for interviewer: {}", request.getInterviewerId());

        Profile interviewer = profileRepository.findById(request.getInterviewerId())
                .orElseThrow(() -> new EntityNotFoundException("Interviewer not found: " + request.getInterviewerId()));

        Interview interview = new Interview();
        interview.setInterviewer(interviewer);
        interview.setCandidateInfo(request.getCandidateInfo());
        interview.setTechStack(request.getTechStack());
        interview.setStartTime(request.getStartTime());
        interview.setEndTime(request.getEndTime());
        interview.setStatus(InterviewStatus.SCHEDULED);

        interview = interviewRepository.save(interview);

        try {
            String googleEventId = googleCalendarService.createEvent(interviewer, interview);
            interview.setGoogleEventId(googleEventId);
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
    public Interview updateInterview(UUID id, UpdateInterviewRequest request) {
        Interview interview = findById(id);

        interview.setCandidateInfo(request.getCandidateInfo());
        interview.setTechStack(request.getTechStack());
        interview.setStartTime(request.getStartTime());
        interview.setEndTime(request.getEndTime());
        interview.setStatus(request.getStatus());

        interview = interviewRepository.save(interview);

        if (interview.getGoogleEventId() != null) {
            try {
                googleCalendarService.updateEvent(interview.getInterviewer(), interview);
            } catch (Exception e) {
                log.warn("Failed to update Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        return interview;
    }

    @Transactional
    public void deleteInterview(UUID id) {
        Interview interview = findById(id);

        if (interview.getGoogleEventId() != null) {
            try {
                googleCalendarService.deleteEvent(interview.getInterviewer(), interview.getGoogleEventId());
            } catch (Exception e) {
                log.warn("Failed to delete Google Calendar event {}: {}", interview.getGoogleEventId(), e.getMessage());
            }
        }

        interviewRepository.delete(interview);
    }
}
