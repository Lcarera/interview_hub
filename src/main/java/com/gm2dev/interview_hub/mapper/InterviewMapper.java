package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.dto.InterviewSummaryDto;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {ProfileMapper.class, ShadowingRequestMapper.class, CandidateMapper.class})
public interface InterviewMapper {
    InterviewDto toDto(Interview interview);
    InterviewSummaryDto toSummaryDto(Interview interview);

    @Mapping(target = "candidate", ignore = true)
    @Mapping(target = "talentAcquisition", ignore = true)
    @Mapping(target = "interviewer", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "googleEventId", ignore = true)
    @Mapping(target = "shadowingRequests", ignore = true)
    void updateFromRequest(UpdateInterviewRequest request, @MappingTarget Interview interview);
}
