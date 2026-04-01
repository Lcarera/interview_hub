package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.gm2dev.interview_hub.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InternalEmailController {

    private final EmailService emailService;

    @PostMapping("/email-worker")
    public ResponseEntity<Void> processEmailTask(@RequestBody @Valid EmailTaskPayload payload) {
        log.debug("Processing {} email for {}", payload.getClass().getSimpleName(), payload.to());
        emailService.sendDirectly(payload);
        return ResponseEntity.ok().build();
    }
}
