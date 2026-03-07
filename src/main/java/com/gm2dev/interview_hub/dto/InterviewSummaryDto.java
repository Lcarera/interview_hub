package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import java.time.Instant;
import java.util.UUID;

@Value
@Schema(description = "Abbreviated interview details used in nested contexts")
public class InterviewSummaryDto {
    @Schema(description = "Interview UUID")
    UUID id;

    @Schema(description = "Technology stack", example = "Java/Spring")
    String techStack;

    @Schema(description = "Start time (ISO-8601)", example = "2026-04-20T15:00:00Z")
    Instant startTime;

    @Schema(description = "End time (ISO-8601)", example = "2026-04-20T16:00:00Z")
    Instant endTime;

    @Schema(description = "Current status", example = "SCHEDULED")
    InterviewStatus status;
}
