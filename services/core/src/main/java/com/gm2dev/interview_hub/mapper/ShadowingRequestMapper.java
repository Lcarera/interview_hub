package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.dto.ShadowingRequestDto;
import com.gm2dev.interview_hub.dto.ShadowingRequestSummaryDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ProfileMapper.class})
public interface ShadowingRequestMapper {
    ShadowingRequestDto toDto(ShadowingRequest request);
    ShadowingRequestSummaryDto toSummaryDto(ShadowingRequest request);
}
