package com.gm2dev.interview_hub.dto;

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
@Schema(description = "Request body for creating a new interview")
public class CreateInterviewRequest {

    @NotNull
    @Schema(description = "UUID of the interviewer profile", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID interviewerId;

    @NotNull
    @Schema(description = "UUID of the candidate", example = "660e8400-e29b-41d4-a716-446655440001")
    private UUID candidateId;

    @Schema(description = "UUID of the talent acquisition contact (optional)", example = "770e8400-e29b-41d4-a716-446655440002")
    private UUID talentAcquisitionId;

    @NotBlank
    @Schema(description = "Technology stack for the interview", example = "Java/Spring")
    private String techStack;

    @NotNull
    @Future
    @Schema(description = "Interview start time (ISO-8601)", example = "2026-04-20T15:00:00Z")
    private Instant startTime;

    @NotNull
    @Future
    @Schema(description = "Interview end time (ISO-8601)", example = "2026-04-20T16:00:00Z")
    private Instant endTime;
}
