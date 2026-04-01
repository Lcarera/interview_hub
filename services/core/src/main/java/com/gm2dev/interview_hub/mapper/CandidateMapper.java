package com.gm2dev.interview_hub.mapper;

import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.dto.CandidateDto;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CandidateMapper {
    CandidateDto toDto(Candidate candidate);
    void updateFromRequest(CandidateRequest request, @MappingTarget Candidate candidate);
}
