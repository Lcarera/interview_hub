package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.CreateInterviewRequest;
import com.gm2dev.interview_hub.dto.CurrentUser;
import com.gm2dev.interview_hub.dto.InterviewDto;
import com.gm2dev.interview_hub.dto.UpdateInterviewRequest;
import com.gm2dev.interview_hub.mapper.InterviewMapper;
import com.gm2dev.interview_hub.service.InterviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews", description = "CRUD operations for interviews with Google Calendar sync")
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewMapper interviewMapper;

    @Operation(summary = "Create an interview", description = "Creates a new interview and a Google Calendar event on the interviewer's calendar.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Interview created"),
                    @ApiResponse(responseCode = "400", description = "Validation error")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewDto createInterview(@Valid @RequestBody CreateInterviewRequest request) {
        return interviewMapper.toDto(interviewService.createInterview(request));
    }

    @Operation(summary = "List interviews (paginated)", description = "Supports page, size, and sort query parameters via Spring Data Pageable.")
    @GetMapping
    public Page<InterviewDto> listInterviews(@Parameter(hidden = true) Pageable pageable) {
        return interviewService.findAll(pageable).map(interviewMapper::toDto);
    }

    @Operation(summary = "Get an interview by ID", responses = {
            @ApiResponse(responseCode = "200", description = "Interview found"),
            @ApiResponse(responseCode = "404", description = "Interview not found")})
    @GetMapping("/{id}")
    public InterviewDto getInterview(@PathVariable UUID id) {
        return interviewMapper.toDto(interviewService.findById(id));
    }

    @Operation(summary = "Update an interview", description = "Updates an interview and syncs changes to the Google Calendar event.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Interview updated"),
                    @ApiResponse(responseCode = "404", description = "Interview not found"),
                    @ApiResponse(responseCode = "400", description = "Validation error")})
    @PutMapping("/{id}")
    public InterviewDto updateInterview(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateInterviewRequest request,
                                        CurrentUser currentUser) {
        return interviewMapper.toDto(interviewService.updateInterview(id, request, currentUser.id()));
    }

    @Operation(summary = "Delete an interview", description = "Deletes an interview and cancels the Google Calendar event.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Interview deleted"),
                    @ApiResponse(responseCode = "404", description = "Interview not found")})
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterview(@PathVariable UUID id, CurrentUser currentUser) {
        interviewService.deleteInterview(id, currentUser.id());
    }
}
