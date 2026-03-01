package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import lombok.Value;
import java.time.Instant;
import java.util.UUID;

@Value
public class InterviewSummaryDto {
    UUID id;
    String techStack;
    Instant startTime;
    Instant endTime;
    InterviewStatus status;
}
