package com.gm2dev.calendar_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private CalendarEventRequest buildRequest(String googleEventId) {
        return new CalendarEventRequest(
                googleEventId,
                "Java",
                "Alice Smith",
                "alice@example.com",
                "https://linkedin.com/in/alice",
                "Backend",
                "https://feedback.link/123",
                "interviewer@gm2dev.com",
                List.of("shadow@gm2dev.com"),
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T11:00:00Z")
        );
    }

    @Test
    void postEvents_createsEventAndReturnsResponse() throws Exception {
        CalendarEventRequest request = buildRequest(null);
        CalendarEventResponse response = new CalendarEventResponse("evt-abc123", "https://meet.google.com/xyz");
        when(googleCalendarService.createEvent(any())).thenReturn(response);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-abc123"))
                .andExpect(jsonPath("$.meetLink").value("https://meet.google.com/xyz"));

        verify(googleCalendarService).createEvent(any());
    }

    @Test
    void putEventsEventId_updatesEventAndReturns204() throws Exception {
        CalendarEventRequest request = buildRequest("evt-abc123");
        doNothing().when(googleCalendarService).updateEvent(any());

        mockMvc.perform(put("/events/evt-abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).updateEvent(any());
    }

    @Test
    void deleteEventsEventId_deletesEventAndReturns204() throws Exception {
        doNothing().when(googleCalendarService).deleteEvent(eq("evt-abc123"));

        mockMvc.perform(delete("/events/evt-abc123"))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).deleteEvent("evt-abc123");
    }

    @Test
    void postEventsEventIdAttendees_addsAttendeeAndReturns204() throws Exception {
        AttendeeRequest attendeeRequest = new AttendeeRequest("evt-abc123", "shadow@gm2dev.com");
        doNothing().when(googleCalendarService).addAttendee(any());

        mockMvc.perform(post("/events/evt-abc123/attendees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(attendeeRequest)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).addAttendee(any());
    }

    @Test
    void deleteEventsEventIdAttendees_removesAttendeeAndReturns204() throws Exception {
        AttendeeRequest attendeeRequest = new AttendeeRequest("evt-abc123", "shadow@gm2dev.com");
        doNothing().when(googleCalendarService).removeAttendee(any());

        mockMvc.perform(delete("/events/evt-abc123/attendees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(attendeeRequest)))
                .andExpect(status().isNoContent());

        verify(googleCalendarService).removeAttendee(any());
    }

    @Test
    void postEvents_whenCalendarServiceThrows_returns500() throws Exception {
        CalendarEventRequest request = buildRequest(null);
        when(googleCalendarService.createEvent(any())).thenThrow(new RuntimeException("Google Calendar unavailable"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}
