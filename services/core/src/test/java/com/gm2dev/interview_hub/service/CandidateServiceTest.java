package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import com.gm2dev.interview_hub.repository.InterviewRepository;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class CandidateServiceTest {

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private com.gm2dev.interview_hub.client.CalendarServiceClient calendarServiceClient;

    @Test
    void createCandidate_withValidRequest_returnsCandidate() {
        CandidateRequest request = new CandidateRequest(
                "Jane Doe", "jane@example.com", "https://linkedin.com/in/janedoe", "Java", "https://feedback.link/123"
        );

        Candidate candidate = candidateService.createCandidate(request);

        assertNotNull(candidate.getId());
        assertEquals("Jane Doe", candidate.getName());
        assertEquals("jane@example.com", candidate.getEmail());
        assertEquals("https://linkedin.com/in/janedoe", candidate.getLinkedinUrl());
        assertEquals("Java", candidate.getPrimaryArea());
        assertEquals("https://feedback.link/123", candidate.getFeedbackLink());
    }

    @Test
    void findById_withExistingId_returnsCandidate() {
        CandidateRequest request = new CandidateRequest(
                "Jane Doe", "jane@example.com", null, null, null
        );
        Candidate created = candidateService.createCandidate(request);

        Candidate found = candidateService.findById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("Jane Doe", found.getName());
    }

    @Test
    void findById_withNonExistentId_throwsEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        assertThrows(EntityNotFoundException.class, () -> candidateService.findById(nonExistentId));
    }

    @Test
    void findAll_returnsAllCandidates() {
        candidateService.createCandidate(new CandidateRequest("A", "a@test.com", null, null, null));
        candidateService.createCandidate(new CandidateRequest("B", "b@test.com", null, null, null));

        var all = candidateService.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void updateCandidate_withValidRequest_updatesFields() {
        Candidate created = candidateService.createCandidate(
                new CandidateRequest("Jane Doe", "jane@example.com", null, "Java", null)
        );

        CandidateRequest update = new CandidateRequest(
                "Jane Smith", "jane.smith@example.com", "https://linkedin.com/in/janesmith", "React", "https://feedback.link/456"
        );
        Candidate updated = candidateService.updateCandidate(created.getId(), update);

        assertEquals("Jane Smith", updated.getName());
        assertEquals("jane.smith@example.com", updated.getEmail());
        assertEquals("https://linkedin.com/in/janesmith", updated.getLinkedinUrl());
        assertEquals("React", updated.getPrimaryArea());
        assertEquals("https://feedback.link/456", updated.getFeedbackLink());
    }

    @Test
    void deleteCandidate_withExistingId_deletesCandidate() {
        Candidate created = candidateService.createCandidate(
                new CandidateRequest("Jane Doe", "jane@example.com", null, null, null)
        );

        candidateService.deleteCandidate(created.getId());

        assertThrows(EntityNotFoundException.class, () -> candidateService.findById(created.getId()));
    }

    @Test
    void deleteCandidate_withNonExistentId_throwsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class, () -> candidateService.deleteCandidate(UUID.randomUUID()));
    }

    @Test
    void deleteCandidate_withExistingInterviews_throwsIllegalStateException() {
        Candidate candidate = candidateService.createCandidate(
                new CandidateRequest("Jane Doe", "jane@example.com", null, null, null)
        );

        Profile interviewer = new Profile(UUID.randomUUID(), "interviewer@gm2dev.com", Role.interviewer);
        profileRepository.save(interviewer);
        entityManager.flush();

        Interview interview = new Interview();
        interview.setInterviewer(interviewer);
        interview.setCandidate(candidate);
        interview.setStartTime(Instant.now());
        interview.setEndTime(Instant.now().plusSeconds(3600));
        interview.setStatus(InterviewStatus.SCHEDULED);
        interviewRepository.save(interview);
        entityManager.flush();

        assertThrows(IllegalStateException.class, () -> candidateService.deleteCandidate(candidate.getId()));
    }
}
