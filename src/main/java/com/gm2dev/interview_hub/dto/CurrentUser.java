package com.gm2dev.interview_hub.dto;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

public record CurrentUser(UUID id) {
    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()));
    }
}
