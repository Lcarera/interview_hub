package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import java.util.UUID;

@Value
@Schema(description = "User profile details")
public class ProfileDto {
    @Schema(description = "Profile UUID")
    UUID id;

    @Schema(description = "Email address", example = "user@gm2dev.com")
    String email;

    @Schema(description = "User role", example = "interviewer")
    Role role;

    @Schema(description = "Google Calendar email (may differ from login email)")
    String calendarEmail;
}
