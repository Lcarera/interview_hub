package com.gm2dev.interview_hub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String linkedinUrl;
    private String primaryArea;
    private String feedbackLink;
}
