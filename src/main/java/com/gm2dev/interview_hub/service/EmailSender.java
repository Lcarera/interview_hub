package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.dto.EmailTaskPayload;

public interface EmailSender {
    void send(EmailTaskPayload payload);
}
