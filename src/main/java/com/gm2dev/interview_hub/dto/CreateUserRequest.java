package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotNull Role role
) {}
