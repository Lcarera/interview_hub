package com.gm2dev.interview_hub.service;

import com.gm2dev.shared.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailPublisher {

    private final StreamBridge streamBridge;

    public void publish(EmailMessage message) {
        log.debug("Publishing {} email for {}", message.getClass().getSimpleName(), message.to());
        streamBridge.send("email-out-0", message);
    }
}
