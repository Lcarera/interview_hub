package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public Profile getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID profileId = UUID.fromString(jwt.getSubject());
        return profileService.findById(profileId);
    }

    @GetMapping
    public List<Profile> listProfiles() {
        return profileService.findAll();
    }
}
