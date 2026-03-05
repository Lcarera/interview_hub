package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import lombok.Value;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
public class InterviewDto {
    UUID id;
    ProfileDto interviewer;
    CandidateDto candidate;
    ProfileDto talentAcquisition;
    String techStack;
    Instant startTime;
    Instant endTime;
    InterviewStatus status;
    List<ShadowingRequestSummaryDto> shadowingRequests;
}
