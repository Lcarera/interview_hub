package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Interview createInterview(@Valid @RequestBody CreateInterviewRequest request) {
        return interviewService.createInterview(request);
    }

    @GetMapping
    public Page<Interview> listInterviews(Pageable pageable) {
        return interviewService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public Interview getInterview(@PathVariable UUID id) {
        return interviewService.findById(id);
    }

    @PutMapping("/{id}")
    public Interview updateInterview(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateInterviewRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return interviewService.updateInterview(id, request, requesterId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterview(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        interviewService.deleteInterview(id, requesterId);
    }
}
