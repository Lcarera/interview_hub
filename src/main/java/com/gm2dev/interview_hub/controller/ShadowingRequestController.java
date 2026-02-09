package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.service.ShadowingRequestService;
import com.gm2dev.interview_hub.service.dto.CreateShadowingRequest;
import com.gm2dev.interview_hub.service.dto.RejectShadowingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShadowingRequestController {

    private final ShadowingRequestService shadowingRequestService;

    @PostMapping("/api/interviews/{interviewId}/shadowing-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ShadowingRequest requestShadowing(@PathVariable UUID interviewId,
                                             @Valid @RequestBody CreateShadowingRequest request) {
        return shadowingRequestService.requestShadowing(interviewId, request.getShadowerId());
    }

    @PostMapping("/api/shadowing-requests/{id}/cancel")
    public ShadowingRequest cancelShadowingRequest(@PathVariable UUID id) {
        return shadowingRequestService.cancelShadowingRequest(id);
    }

    @PostMapping("/api/shadowing-requests/{id}/approve")
    public ShadowingRequest approveShadowingRequest(@PathVariable UUID id) {
        return shadowingRequestService.approveShadowingRequest(id);
    }

    @PostMapping("/api/shadowing-requests/{id}/reject")
    public ShadowingRequest rejectShadowingRequest(@PathVariable UUID id,
                                                    @RequestBody RejectShadowingRequest request) {
        return shadowingRequestService.rejectShadowingRequest(id, request.getReason());
    }
}
