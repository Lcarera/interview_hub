package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for updating an existing interview")
public class UpdateInterviewRequest {

    @NotNull
    @Schema(description = "UUID of the candidate", example = "660e8400-e29b-41d4-a716-446655440001")
    private UUID candidateId;

    @Schema(description = "UUID of the talent acquisition contact (optional)", example = "770e8400-e29b-41d4-a716-446655440002")
    private UUID talentAcquisitionId;

    @NotBlank
    @Schema(description = "Technology stack for the interview", example = "Java/Spring/Hibernate")
    private String techStack;

    @NotNull
    @Future
    @Schema(description = "Interview start time (ISO-8601)", example = "2026-04-20T16:00:00Z")
    private Instant startTime;

    @NotNull
    @Future
    @Schema(description = "Interview end time (ISO-8601)", example = "2026-04-20T17:00:00Z")
    private Instant endTime;

    @NotNull
    @Schema(description = "Interview status", example = "SCHEDULED")
    private InterviewStatus status;
}
