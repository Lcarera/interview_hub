package com.gm2dev.interview_hub.dto;

import lombok.Value;
import java.util.UUID;

@Value
public class CandidateDto {
    UUID id;
    String name;
    String email;
    String linkedinUrl;
    String primaryArea;
    String feedbackLink;
}
