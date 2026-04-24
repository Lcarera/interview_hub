package com.gm2dev.calendar_service;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping
    public ResponseEntity<CalendarEventResponse> createEvent(@RequestBody CalendarEventRequest request) throws IOException {
        CalendarEventResponse response = googleCalendarService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<Void> updateEvent(
            @PathVariable String eventId,
            @RequestBody CalendarEventRequest request) throws IOException {
        googleCalendarService.updateEvent(eventId, request);
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
        googleCalendarService.addAttendee(eventId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/attendees")
    public ResponseEntity<Void> removeAttendee(
            @PathVariable String eventId,
            @RequestBody AttendeeRequest request) throws IOException {
        googleCalendarService.removeAttendee(eventId, request);
        return ResponseEntity.noContent().build();
    }
}
