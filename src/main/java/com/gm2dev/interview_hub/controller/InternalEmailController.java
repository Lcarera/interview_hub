package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.dto.EmailTaskPayload;
import com.gm2dev.interview_hub.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalEmailController {

    private final EmailService emailService;

    @PostMapping("/email-worker")
    public ResponseEntity<Void> processEmailTask(@RequestBody @Valid EmailTaskPayload payload) {
        log.debug("Processing {} email for {}", payload.getClass().getSimpleName(), payload.to());

        switch (payload) {
            case EmailTaskPayload.VerificationEmail e ->
                    emailService.sendVerificationEmail(e.to(), e.token());
            case EmailTaskPayload.PasswordResetEmail e ->
                    emailService.sendPasswordResetEmail(e.to(), e.token());
            case EmailTaskPayload.TemporaryPasswordEmail e ->
                    emailService.sendTemporaryPasswordEmail(e.to(), e.temporaryPassword());
            case EmailTaskPayload.ShadowingApprovedEmail e ->
                    emailService.sendShadowingApprovedEmail(e.to(), e.summary(), e.startTime(), e.endTime());
        }

        return ResponseEntity.ok().build();
    }
}
