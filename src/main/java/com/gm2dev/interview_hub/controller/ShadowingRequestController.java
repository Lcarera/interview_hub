package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.dto.RejectShadowingRequest;
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

    @GetMapping("/api/interviews/{interviewId}/shadowing-requests")
    public List<ShadowingRequest> listByInterview(@PathVariable UUID interviewId) {
        return shadowingRequestService.findByInterviewId(interviewId);
    }

    @GetMapping("/api/shadowing-requests/my")
    public List<ShadowingRequest> listMyShadowingRequests(@AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.findByShadowerId(shadowerId);
    }

    @PostMapping("/api/interviews/{interviewId}/shadowing-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ShadowingRequest requestShadowing(@PathVariable UUID interviewId,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.requestShadowing(interviewId, shadowerId);
    }

    @PostMapping("/api/shadowing-requests/{id}/cancel")
    public ShadowingRequest cancelShadowingRequest(@PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.cancelShadowingRequest(id, requesterId);
    }

    @PostMapping("/api/shadowing-requests/{id}/approve")
    public ShadowingRequest approveShadowingRequest(@PathVariable UUID id,
                                                    @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.approveShadowingRequest(id, requesterId);
    }

    @PostMapping("/api/shadowing-requests/{id}/reject")
    public ShadowingRequest rejectShadowingRequest(@PathVariable UUID id,
                                                   @RequestBody RejectShadowingRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.rejectShadowingRequest(id, request.getReason(), requesterId);
    }
}
