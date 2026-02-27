package com.gm2dev.interview_hub.dto;

public record AuthResponse(String token, int expiresIn, String email) {
}
