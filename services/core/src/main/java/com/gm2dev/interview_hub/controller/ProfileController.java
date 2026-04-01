package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.CurrentUser;
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.mapper.ProfileMapper;
import com.gm2dev.interview_hub.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@Tag(name = "Profiles", description = "User profile operations")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileMapper profileMapper;

    @Operation(summary = "Get my profile", description = "Returns the authenticated user's profile.")
    @GetMapping("/me")
    public ProfileDto getMyProfile(@Parameter(hidden = true) CurrentUser currentUser) {
        return profileMapper.toDto(profileService.findById(currentUser.id()));
    }

    @Operation(summary = "List all profiles")
    @GetMapping
    public List<ProfileDto> listProfiles() {
        return profileService.findAll().stream()
                .map(profileMapper::toDto)
                .toList();
    }
}
