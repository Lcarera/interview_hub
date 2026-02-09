package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.service.dto.UpdateInterviewRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public Interview createInterview(CreateInterviewRequest request) {
        log.debug("Creating interview for interviewer: {}", request.getInterviewerId());

        Profile interviewer = profileRepository.findById(request.getInterviewerId())
                .orElseThrow(() -> new EntityNotFoundException("Interviewer not found: " + request.getInterviewerId()));

        Interview interview = new Interview();
        interview.setInterviewer(interviewer);
        interview.setGoogleEventId(request.getGoogleEventId());
        interview.setCandidateInfo(request.getCandidateInfo());
        interview.setTechStack(request.getTechStack());
        interview.setStartTime(request.getStartTime());
        interview.setEndTime(request.getEndTime());
        interview.setStatus(InterviewStatus.SCHEDULED);

        return interviewRepository.save(interview);
    }

    @Transactional(readOnly = true)
    public List<Interview> findAll() {
        return interviewRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Interview findById(UUID id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
    }

    @Transactional
    public Interview updateInterview(UUID id, UpdateInterviewRequest request) {
        Interview interview = findById(id);

        interview.setGoogleEventId(request.getGoogleEventId());
        interview.setCandidateInfo(request.getCandidateInfo());
        interview.setTechStack(request.getTechStack());
        interview.setStartTime(request.getStartTime());
        interview.setEndTime(request.getEndTime());
        interview.setStatus(request.getStatus());

        return interviewRepository.save(interview);
    }

    @Transactional
    public void deleteInterview(UUID id) {
        Interview interview = findById(id);
        interviewRepository.delete(interview);
    }
}
