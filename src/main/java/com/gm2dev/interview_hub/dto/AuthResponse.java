package com.gm2dev.interview_hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after successful OAuth authentication")
public record AuthResponse(
        @Schema(description = "App-issued JWT token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Token validity in seconds", example = "3600")
        int expiresIn,

        @Schema(description = "Authenticated user's email", example = "user@gm2dev.com")
        String email
) {
}
