package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.AuthResponse;

public interface JwtService {
    AuthResponse issueToken(Profile profile);
}
