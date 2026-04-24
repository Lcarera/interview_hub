package com.gm2dev.calendar_service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Void> handleCalendarException(IOException e) {
        log.error("Calendar operation failed: {}", e.getMessage());
        return ResponseEntity.internalServerError().build();
    }
}
