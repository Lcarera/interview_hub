package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.dto.InterviewSummaryDto;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {ProfileMapper.class, ShadowingRequestMapper.class})
public interface InterviewMapper {
    InterviewDto toDto(Interview interview);
    InterviewSummaryDto toSummaryDto(Interview interview);
    void updateFromRequest(UpdateInterviewRequest request, @MappingTarget Interview interview);
}
