package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
import com.gm2dev.interview_hub.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileMapper profileMapper;

    @GetMapping("/me")
    public ProfileDto getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID profileId = UUID.fromString(jwt.getSubject());
        return profileMapper.toDto(profileService.findById(profileId));
    }

    @GetMapping
    public List<ProfileDto> listProfiles() {
        return profileService.findAll().stream()
                .map(profileMapper::toDto)
                .toList();
    }
}
