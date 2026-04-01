package com.gm2dev.notification_service;

import com.gm2dev.shared.email.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class EmailConsumer {

    @Bean
    public Consumer<EmailMessage> processEmail(ResendEmailSender sender) {
        return message -> {
            log.debug("Received {} for {}", message.getClass().getSimpleName(), message.to());
            sender.send(message);
        };
    }
}
