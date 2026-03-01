package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.RejectShadowingRequest;
import com.gm2dev.interview_hub.dto.ShadowingRequestDto;
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapper;
import com.gm2dev.interview_hub.service.ShadowingRequestService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShadowingRequestController {

    private final ShadowingRequestService shadowingRequestService;
    private final ShadowingRequestMapper shadowingRequestMapper;

    @GetMapping("/api/interviews/{interviewId}/shadowing-requests")
    public List<ShadowingRequestDto> listByInterview(@PathVariable UUID interviewId) {
        return shadowingRequestService.findByInterviewId(interviewId).stream()
                .map(shadowingRequestMapper::toDto)
                .toList();
    }

    @GetMapping("/api/shadowing-requests/my")
    public List<ShadowingRequestDto> listMyShadowingRequests(@AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.findByShadowerId(shadowerId).stream()
                .map(shadowingRequestMapper::toDto)
                .toList();
    }

    @PostMapping("/api/interviews/{interviewId}/shadowing-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ShadowingRequestDto requestShadowing(@PathVariable UUID interviewId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(
                shadowingRequestService.requestShadowing(interviewId, shadowerId));
    }

    @PostMapping("/api/shadowing-requests/{id}/cancel")
    public ShadowingRequestDto cancelShadowingRequest(@PathVariable UUID id,
                                                       @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(shadowingRequestService.cancelShadowingRequest(id, requesterId));
    }

    @PostMapping("/api/shadowing-requests/{id}/approve")
    public ShadowingRequestDto approveShadowingRequest(@PathVariable UUID id,
                                                        @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(shadowingRequestService.approveShadowingRequest(id, requesterId));
    }

    @PostMapping("/api/shadowing-requests/{id}/reject")
    public ShadowingRequestDto rejectShadowingRequest(@PathVariable UUID id,
                                                       @RequestBody RejectShadowingRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(
                shadowingRequestService.rejectShadowingRequest(id, request.getReason(), requesterId));
    }
}
