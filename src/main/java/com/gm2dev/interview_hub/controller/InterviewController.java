package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.mapper.InterviewMapper;
import com.gm2dev.interview_hub.service.InterviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewMapper interviewMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewDto createInterview(@Valid @RequestBody CreateInterviewRequest request) {
        return interviewMapper.toDto(interviewService.createInterview(request));
    }

    @GetMapping
    public Page<InterviewDto> listInterviews(Pageable pageable) {
        return interviewService.findAll(pageable).map(interviewMapper::toDto);
    }

    @GetMapping("/{id}")
    public InterviewDto getInterview(@PathVariable UUID id) {
        return interviewMapper.toDto(interviewService.findById(id));
    }

    @PutMapping("/{id}")
    public InterviewDto updateInterview(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateInterviewRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return interviewMapper.toDto(interviewService.updateInterview(id, request, requesterId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterview(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        interviewService.deleteInterview(id, requesterId);
    }
}
