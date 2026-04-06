package com.gm2dev.calendar_service;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final GoogleCalendarService googleCalendarService;

    @ExceptionHandler({IOException.class, RuntimeException.class})
    public ResponseEntity<Void> handleCalendarError(Exception e) {
        log.error("Calendar operation failed: {}", e.getMessage());
        return ResponseEntity.internalServerError().build();
    }

    @PostMapping
    public ResponseEntity<CalendarEventResponse> createEvent(@RequestBody CalendarEventRequest request) throws IOException {
        CalendarEventResponse response = googleCalendarService.createEvent(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<Void> updateEvent(
            @PathVariable String eventId,
            @RequestBody CalendarEventRequest request) throws IOException {
        googleCalendarService.updateEvent(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String eventId) throws IOException {
        googleCalendarService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/attendees")
    public ResponseEntity<Void> addAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        googleCalendarService.addAttendee(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/attendees")
    public ResponseEntity<Void> removeAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        googleCalendarService.removeAttendee(request);
        return ResponseEntity.noContent().build();
    }
}
