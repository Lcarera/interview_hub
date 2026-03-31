package com.gm2dev.interview_hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating or updating a candidate")
public class CandidateRequest {

    @NotBlank
    @Schema(description = "Full name", example = "Jane Doe")
    private String name;

    @NotBlank
    @Email
    @Schema(description = "Email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "LinkedIn profile URL", example = "https://linkedin.com/in/janedoe")
    private String linkedinUrl;

    @Schema(description = "Primary technical area", example = "Backend")
    private String primaryArea;

    @Schema(description = "Link to feedback form or document")
    private String feedbackLink;
}
