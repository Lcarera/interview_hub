package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.RejectShadowingRequest;
import com.gm2dev.interview_hub.dto.ShadowingRequestDto;
import com.gm2dev.interview_hub.mapper.ShadowingRequestMapper;
import com.gm2dev.interview_hub.service.ShadowingRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Shadowing Requests", description = "Manage requests to observe interviews")
public class ShadowingRequestController {

    private final ShadowingRequestService shadowingRequestService;
    private final ShadowingRequestMapper shadowingRequestMapper;

    @Operation(summary = "List shadowing requests for an interview")
    @GetMapping("/api/interviews/{interviewId}/shadowing-requests")
    public List<ShadowingRequestDto> listByInterview(@PathVariable UUID interviewId) {
        return shadowingRequestService.findByInterviewId(interviewId).stream()
                .map(shadowingRequestMapper::toDto)
                .toList();
    }

    @Operation(summary = "List my shadowing requests", description = "Returns shadowing requests for the authenticated user.")
    @GetMapping("/api/shadowing-requests/my")
    public List<ShadowingRequestDto> listMyShadowingRequests(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestService.findByShadowerId(shadowerId).stream()
                .map(shadowingRequestMapper::toDto)
                .toList();
    }

    @Operation(summary = "Request to shadow an interview", description = "Shadower identity is derived from the Bearer token.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Shadowing request created"),
                    @ApiResponse(responseCode = "404", description = "Interview not found")})
    @PostMapping("/api/interviews/{interviewId}/shadowing-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ShadowingRequestDto requestShadowing(@PathVariable UUID interviewId,
                                                @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID shadowerId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(
                shadowingRequestService.requestShadowing(interviewId, shadowerId));
    }

    @Operation(summary = "Cancel a shadowing request", responses = {
            @ApiResponse(responseCode = "200", description = "Request cancelled"),
            @ApiResponse(responseCode = "404", description = "Request not found")})
    @PostMapping("/api/shadowing-requests/{id}/cancel")
    public ShadowingRequestDto cancelShadowingRequest(@PathVariable UUID id,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(shadowingRequestService.cancelShadowingRequest(id, requesterId));
    }

    @Operation(summary = "Approve a shadowing request", description = "Adds the shadower as an attendee to the Google Calendar event.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Request approved"),
                    @ApiResponse(responseCode = "404", description = "Request not found")})
    @PostMapping("/api/shadowing-requests/{id}/approve")
    public ShadowingRequestDto approveShadowingRequest(@PathVariable UUID id,
                                                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(shadowingRequestService.approveShadowingRequest(id, requesterId));
    }

    @Operation(summary = "Reject a shadowing request", responses = {
            @ApiResponse(responseCode = "200", description = "Request rejected"),
            @ApiResponse(responseCode = "404", description = "Request not found")})
    @PostMapping("/api/shadowing-requests/{id}/reject")
    public ShadowingRequestDto rejectShadowingRequest(@PathVariable UUID id,
                                                       @RequestBody RejectShadowingRequest request,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        return shadowingRequestMapper.toDto(
                shadowingRequestService.rejectShadowingRequest(id, request.getReason(), requesterId));
    }
}
