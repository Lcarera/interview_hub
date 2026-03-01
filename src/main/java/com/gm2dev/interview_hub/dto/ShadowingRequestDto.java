package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import lombok.Value;
import java.util.UUID;

@Value
public class ShadowingRequestDto {
    UUID id;
    InterviewSummaryDto interview;
    ProfileDto shadower;
    ShadowingRequestStatus status;
    String reason;
}
