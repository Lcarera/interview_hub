package com.gm2dev.interview_hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import java.util.UUID;

@Value
@Schema(description = "Candidate details")
public class CandidateDto {
    @Schema(description = "Candidate UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;

    @Schema(description = "Full name", example = "Jane Doe")
    String name;

    @Schema(description = "Email address", example = "jane.doe@example.com")
    String email;

    @Schema(description = "LinkedIn profile URL", example = "https://linkedin.com/in/janedoe")
    String linkedinUrl;

    @Schema(description = "Primary technical area", example = "Backend")
    String primaryArea;

    @Schema(description = "Link to feedback form or document")
    String feedbackLink;
}
