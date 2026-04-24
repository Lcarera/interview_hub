package com.gm2dev.calendar_service;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final GoogleCalendarService googleCalendarService;

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Void> handleCalendarError(IOException e) {
        log.error("Calendar operation failed: {}", e.getMessage());
        return ResponseEntity.internalServerError().build();
    }

    @ExceptionHandler(EventIdMismatchException.class)
    public ResponseEntity<String> handleEventIdMismatch(EventIdMismatchException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
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
        validateEventId(eventId, request.googleEventId());
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
        validateEventId(eventId, request.googleEventId());
        googleCalendarService.addAttendee(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/attendees/remove")
    public ResponseEntity<Void> removeAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        validateEventId(eventId, request.googleEventId());
        googleCalendarService.removeAttendee(request);
        return ResponseEntity.noContent().build();
    }

    private void validateEventId(String pathEventId, String bodyEventId) {
        if (bodyEventId != null && !pathEventId.equals(bodyEventId)) {
            throw new EventIdMismatchException(pathEventId, bodyEventId);
        }
    }
}
