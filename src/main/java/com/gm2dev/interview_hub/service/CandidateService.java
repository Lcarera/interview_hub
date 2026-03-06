package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import com.gm2dev.interview_hub.mapper.CandidateMapper;
import com.gm2dev.interview_hub.repository.CandidateRepository;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;
    private final InterviewRepository interviewRepository;

    @Transactional
    public Candidate createCandidate(CandidateRequest request) {
        Candidate candidate = new Candidate();
        candidate.setName(request.getName());
        candidate.setEmail(request.getEmail());
        candidate.setLinkedinUrl(request.getLinkedinUrl());
        candidate.setPrimaryArea(request.getPrimaryArea());
        candidate.setFeedbackLink(request.getFeedbackLink());
        return candidateRepository.save(candidate);
    }

    @Transactional(readOnly = true)
    public Candidate findById(UUID id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Candidate> findAll() {
        return candidateRepository.findAll();
    }

    @Transactional
    public Candidate updateCandidate(UUID id, CandidateRequest request) {
        Candidate candidate = findById(id);
        candidateMapper.updateFromRequest(request, candidate);
        return candidateRepository.save(candidate);
    }

    @Transactional
    public void deleteCandidate(UUID id) {
        Candidate candidate = findById(id);
        if (interviewRepository.existsByCandidateId(id)) {
            throw new IllegalStateException("Cannot delete candidate with existing interviews");
        }
        candidateRepository.delete(candidate);
    }
}
