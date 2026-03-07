package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.CandidateDto;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import com.gm2dev.interview_hub.mapper.CandidateMapper;
import com.gm2dev.interview_hub.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidates", description = "CRUD operations for interview candidates")
public class CandidateController {

    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;

    @Operation(summary = "Create a candidate", responses = {
            @ApiResponse(responseCode = "201", description = "Candidate created"),
            @ApiResponse(responseCode = "400", description = "Validation error")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto createCandidate(@Valid @RequestBody CandidateRequest request) {
        return candidateMapper.toDto(candidateService.createCandidate(request));
    }

    @Operation(summary = "List all candidates")
    @GetMapping
    public List<CandidateDto> listCandidates() {
        return candidateService.findAll().stream()
                .map(candidateMapper::toDto)
                .toList();
    }

    @Operation(summary = "Get a candidate by ID", responses = {
            @ApiResponse(responseCode = "200", description = "Candidate found"),
            @ApiResponse(responseCode = "404", description = "Candidate not found")})
    @GetMapping("/{id}")
    public CandidateDto getCandidate(@PathVariable UUID id) {
        return candidateMapper.toDto(candidateService.findById(id));
    }

    @Operation(summary = "Update a candidate", responses = {
            @ApiResponse(responseCode = "200", description = "Candidate updated"),
            @ApiResponse(responseCode = "404", description = "Candidate not found"),
            @ApiResponse(responseCode = "400", description = "Validation error")})
    @PutMapping("/{id}")
    public CandidateDto updateCandidate(@PathVariable UUID id,
                                         @Valid @RequestBody CandidateRequest request) {
        return candidateMapper.toDto(candidateService.updateCandidate(id, request));
    }

    @Operation(summary = "Delete a candidate", description = "Fails with 409 if the candidate has existing interviews.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Candidate deleted"),
                    @ApiResponse(responseCode = "404", description = "Candidate not found"),
                    @ApiResponse(responseCode = "409", description = "Candidate has existing interviews")})
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable UUID id) {
        candidateService.deleteCandidate(id);
    }
}
