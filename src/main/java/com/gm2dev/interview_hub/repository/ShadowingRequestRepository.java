package com.gm2dev.interview_hub.repository;

import com.gm2dev.interview_hub.domain.ShadowingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShadowingRequestRepository extends JpaRepository<ShadowingRequest, UUID> {

    List<ShadowingRequest> findByInterviewId(UUID interviewId);

    List<ShadowingRequest> findByShadowerId(UUID shadowerId);
}
