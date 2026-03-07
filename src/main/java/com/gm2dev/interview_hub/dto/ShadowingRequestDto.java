package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import java.util.UUID;

@Value
@Schema(description = "Full shadowing request details")
public class ShadowingRequestDto {
    @Schema(description = "Shadowing request UUID")
    UUID id;

    @Schema(description = "Interview being shadowed")
    InterviewSummaryDto interview;

    @Schema(description = "Profile of the shadower")
    ProfileDto shadower;

    @Schema(description = "Request status", example = "PENDING")
    ShadowingRequestStatus status;

    @Schema(description = "Rejection reason (only set when rejected)", example = "Interview is at full capacity")
    String reason;
}
