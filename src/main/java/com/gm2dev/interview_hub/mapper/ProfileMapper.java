package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.ProfileDto;
import com.gm2dev.interview_hub.dto.RegisterRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", imports = {UUID.class, Role.class})
public interface ProfileMapper {
    ProfileDto toDto(Profile profile);

    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "calendarEmail", source = "email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "emailVerified", constant = "true")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "googleSub", ignore = true)
    @Mapping(target = "googleAccessToken", ignore = true)
    @Mapping(target = "googleRefreshToken", ignore = true)
    @Mapping(target = "googleTokenExpiry", ignore = true)
    Profile toProfileFromCreateUserRequest(CreateUserRequest request);

    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "calendarEmail", source = "email")
    @Mapping(target = "role", expression = "java(Role.interviewer)")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "googleSub", ignore = true)
    @Mapping(target = "googleAccessToken", ignore = true)
    @Mapping(target = "googleRefreshToken", ignore = true)
    @Mapping(target = "googleTokenExpiry", ignore = true)
    Profile toProfileFromRegisterRequest(RegisterRequest request);
}
