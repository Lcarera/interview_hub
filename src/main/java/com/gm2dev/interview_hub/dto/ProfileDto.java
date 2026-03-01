package com.gm2dev.interview_hub.dto;

import lombok.Value;
import java.util.UUID;

@Value
public class ProfileDto {
    UUID id;
    String email;
    String role;
    String calendarEmail;
}
