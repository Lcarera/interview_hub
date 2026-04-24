package com.gm2dev.calendar_service;

public class EventIdMismatchException extends RuntimeException {
    public EventIdMismatchException(String pathEventId, String bodyEventId) {
        super("Path eventId '" + pathEventId + "' does not match body googleEventId '" + bodyEventId + "'");
    }
}
