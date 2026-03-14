package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull Role role
) {}
