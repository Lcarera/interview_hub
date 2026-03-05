package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.CandidateDto;
import com.gm2dev.interview_hub.dto.CandidateRequest;
import com.gm2dev.interview_hub.mapper.CandidateMapper;
import com.gm2dev.interview_hub.service.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto createCandidate(@Valid @RequestBody CandidateRequest request) {
        return candidateMapper.toDto(candidateService.createCandidate(request));
    }

    @GetMapping
    public List<CandidateDto> listCandidates() {
        return candidateService.findAll().stream()
                .map(candidateMapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public CandidateDto getCandidate(@PathVariable UUID id) {
        return candidateMapper.toDto(candidateService.findById(id));
    }

    @PutMapping("/{id}")
    public CandidateDto updateCandidate(@PathVariable UUID id,
                                         @Valid @RequestBody CandidateRequest request) {
        return candidateMapper.toDto(candidateService.updateCandidate(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable UUID id) {
        candidateService.deleteCandidate(id);
    }
}
