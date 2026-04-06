package com.gm2dev.interview_hub.client;

import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "calendar-service")
public interface CalendarServiceClient {

    @PostMapping("/events")
    CalendarEventResponse createEvent(@RequestBody CalendarEventRequest request);

    @PutMapping("/events/{eventId}")
    void updateEvent(@PathVariable("eventId") String eventId, @RequestBody CalendarEventRequest request);

    @DeleteMapping("/events/{eventId}")
    void deleteEvent(@PathVariable("eventId") String eventId);

    @PostMapping("/events/{eventId}/attendees")
    void addAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);

    @DeleteMapping("/events/{eventId}/attendees")
    void removeAttendee(@PathVariable("eventId") String eventId, @RequestBody AttendeeRequest request);
}
