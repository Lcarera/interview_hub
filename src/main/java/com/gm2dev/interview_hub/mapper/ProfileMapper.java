package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.ProfileDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    ProfileDto toDto(Profile profile);
}
