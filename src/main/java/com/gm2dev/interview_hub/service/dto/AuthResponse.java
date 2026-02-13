package com.gm2dev.interview_hub.service.dto;

public record AuthResponse(String token, int expiresIn, String email) {
}
