package com.gm2dev.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarDtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldRoundTripCalendarEventRequest() throws Exception {
        var req = new CalendarEventRequest(
            null, "Java", "Alice", "alice@example.com",
            "https://linkedin.com/in/alice", "Backend", "https://feedback.link",
            "interviewer@example.com", List.of("shadow@example.com"),
            Instant.parse("2026-04-01T10:00:00Z"), Instant.parse("2026-04-01T11:00:00Z")
        );
        String json = mapper.writeValueAsString(req);
        CalendarEventRequest result = mapper.readValue(json, CalendarEventRequest.class);
        assertThat(result.candidateName()).isEqualTo("Alice");
        assertThat(result.approvedShadowerEmails()).containsExactly("shadow@example.com");
        assertThat(result.googleEventId()).isNull();
    }

    @Test
    void shouldRoundTripCalendarEventResponse() throws Exception {
        var resp = new CalendarEventResponse("evt-123", "https://meet.google.com/abc");
        String json = mapper.writeValueAsString(resp);
        CalendarEventResponse result = mapper.readValue(json, CalendarEventResponse.class);
        assertThat(result.eventId()).isEqualTo("evt-123");
        assertThat(result.meetLink()).isEqualTo("https://meet.google.com/abc");
    }

    @Test
    void shouldRoundTripAttendeeRequest() throws Exception {
        var req = new AttendeeRequest("evt-456", "newattendee@example.com");
        String json = mapper.writeValueAsString(req);
        AttendeeRequest result = mapper.readValue(json, AttendeeRequest.class);
        assertThat(result.googleEventId()).isEqualTo("evt-456");
        assertThat(result.email()).isEqualTo("newattendee@example.com");
    }
}
