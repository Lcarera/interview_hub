package com.gm2dev.calendar_service;

import com.gm2dev.calendar_service.config.GoogleCalendarProperties;
import com.gm2dev.calendar_service.config.GoogleOAuthProperties;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private Calendar calendarClient;

    @Mock
    private Calendar.Events events;

    @Mock
    private Calendar.Events.Insert insert;

    @Mock
    private Calendar.Events.Update update;

    @Mock
    private Calendar.Events.Delete deleteOp;

    @Mock
    private Calendar.Events.Get getOp;

    @Mock
    private Calendar.Events.Patch patchOp;

    private GoogleCalendarService googleCalendarService;
    private GoogleCalendarProperties calendarProperties;
    private GoogleOAuthProperties oAuthProperties;

    private static final Instant START_TIME = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant END_TIME = START_TIME.plus(1, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        calendarProperties = new GoogleCalendarProperties();
        calendarProperties.setId("test-calendar-id");
        calendarProperties.setRefreshToken("test-refresh-token");

        oAuthProperties = new GoogleOAuthProperties();
        oAuthProperties.setClientId("test-client-id");
        oAuthProperties.setClientSecret("test-client-secret");

        googleCalendarService = spy(new GoogleCalendarService(oAuthProperties, calendarProperties));
    }

    private CalendarEventRequest buildRequest(String googleEventId) {
        return new CalendarEventRequest(
                googleEventId,
                "Java",
                "Jane Doe",
                "jane@example.com",
                "https://linkedin.com/in/jane",
                "Backend",
                "https://feedback.link/123",
                "interviewer@gm2dev.com",
                List.of(),
                START_TIME,
                END_TIME
        );
    }

    @Test
    void buildCalendarClient_throwsWhenRefreshTokenBlank() {
        calendarProperties.setRefreshToken("");
        GoogleCalendarService bareService = new GoogleCalendarService(oAuthProperties, calendarProperties);
        assertThrows(IOException.class, bareService::buildCalendarClient);
    }

    @Test
    void createEvent_returnsEventIdAndMeetLink() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("google-event-id-123").setHangoutLink("https://meet.google.com/abc");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        CalendarEventResponse result = googleCalendarService.createEvent(request);

        assertEquals("google-event-id-123", result.eventId());
        assertEquals("https://meet.google.com/abc", result.meetLink());
        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_usesSendUpdatesAll() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-send-updates");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        verify(insert).setSendUpdates("all");
    }

    @Test
    void createEvent_usesConfiguredCalendarId() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-cal-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_buildsEventWithCorrectFields() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-789");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();

        assertTrue(event.getSummary().contains("Java"));
        assertTrue(event.getSummary().contains("Jane Doe"));
        assertNotNull(event.getStart());
        assertNotNull(event.getEnd());
    }

    @Test
    void createEvent_formatsDescriptionCleanly() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-desc");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        String desc = captor.getValue().getDescription();

        assertTrue(desc.contains("Tech Stack: Java"));
        assertTrue(desc.contains("Candidate Details:"));
        assertTrue(desc.contains("Name: Jane Doe"));
        assertTrue(desc.contains("Email: jane@example.com"));
        assertTrue(desc.contains("LinkedIn: https://linkedin.com/in/jane"));
        assertTrue(desc.contains("Primary Area: Backend"));
        assertTrue(desc.contains("Feedback Link: https://feedback.link/123"));
    }

    @Test
    void createEvent_includesInterviewerAndCandidateAsAttendees() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-attendees");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(2, attendees.size());
        assertTrue(attendees.stream().anyMatch(a -> "interviewer@gm2dev.com".equals(a.getEmail())));
        assertTrue(attendees.stream().anyMatch(a -> "jane@example.com".equals(a.getEmail())));
    }

    @Test
    void createEvent_includesApprovedShadowerEmails() throws IOException {
        CalendarEventRequest request = new CalendarEventRequest(
                null, "Java", "Jane Doe", "jane@example.com",
                null, null, null, "interviewer@gm2dev.com",
                List.of("shadower@gm2dev.com"),
                START_TIME, END_TIME
        );

        Event createdEvent = new Event().setId("event-shadowers");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(3, attendees.size());
        assertTrue(attendees.stream().anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void createEvent_withNullCandidateName_usesUnknownInSummary() throws IOException {
        CalendarEventRequest request = new CalendarEventRequest(
                null, "Java", null, "jane@example.com",
                null, null, null, "interviewer@gm2dev.com",
                List.of(), START_TIME, END_TIME
        );

        Event createdEvent = new Event().setId("event-null-name");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_onlyInterviewerAttendee_whenNoCandidateEmail() throws IOException {
        CalendarEventRequest request = new CalendarEventRequest(
                null, "Java", "Jane Doe", null,
                null, null, null, "interviewer@gm2dev.com",
                List.of(), START_TIME, END_TIME
        );

        Event createdEvent = new Event().setId("event-no-candidate-email");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(1, attendees.size());
        assertEquals("interviewer@gm2dev.com", attendees.get(0).getEmail());
    }

    @Test
    void createEvent_includesGoogleMeetConferenceData() throws IOException {
        CalendarEventRequest request = buildRequest(null);

        Event createdEvent = new Event().setId("event-meet");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertNotNull(event.getConferenceData());
        assertEquals("hangoutsMeet",
                event.getConferenceData().getCreateRequest().getConferenceSolutionKey().getType());
        assertNotNull(event.getConferenceData().getCreateRequest().getRequestId());
    }

    @Test
    void updateEvent_updatesExistingEvent() throws IOException {
        CalendarEventRequest request = buildRequest("existing-event-id");

        Event updatedEvent = new Event().setId("existing-event-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(request);

        verify(events).update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void updateEvent_usesSendUpdatesAll() throws IOException {
        CalendarEventRequest request = buildRequest("event-update-sends");

        Event updatedEvent = new Event().setId("event-update-sends");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-update-sends"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(request);

        verify(update).setSendUpdates("all");
    }

    @Test
    void deleteEvent_deletesEvent() throws IOException {
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.delete("test-calendar-id", "event-to-delete")).thenReturn(deleteOp);
        when(deleteOp.setSendUpdates("all")).thenReturn(deleteOp);

        googleCalendarService.deleteEvent("event-to-delete");

        verify(events).delete("test-calendar-id", "event-to-delete");
        verify(deleteOp).setSendUpdates("all");
        verify(deleteOp).execute();
    }

    @Test
    void addAttendee_addsEmailToEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-with-attendees")
                .setAttendees(List.of());

        Event patchedEvent = new Event().setId("event-with-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-with-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-with-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee(new AttendeeRequest("event-with-attendees", "shadower@gm2dev.com"));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-with-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertTrue(patched.getAttendees().stream()
                .anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void addAttendee_withNullAttendeesList_addsAttendee() throws IOException {
        Event existingEvent = new Event().setId("event-null-attendees");

        Event patchedEvent = new Event().setId("event-null-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-null-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-null-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee(new AttendeeRequest("event-null-attendees", "new@gm2dev.com"));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-null-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertEquals(1, patched.getAttendees().size());
        assertEquals("new@gm2dev.com", patched.getAttendees().get(0).getEmail());
    }

    @Test
    void removeAttendee_removesEmailFromEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-to-patch")
                .setAttendees(List.of(
                        new EventAttendee().setEmail("keep@gm2dev.com"),
                        new EventAttendee().setEmail("remove@gm2dev.com")
                ));
        Event patchedEvent = new Event().setId("event-to-patch");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-to-patch")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-to-patch"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee(new AttendeeRequest("event-to-patch", "remove@gm2dev.com"));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-to-patch"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertEquals(1, attendees.size());
        assertEquals("keep@gm2dev.com", attendees.get(0).getEmail());
        assertFalse(attendees.stream().anyMatch(a -> "remove@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void removeAttendee_whenNullAttendees_setsEmptyList() throws IOException {
        Event existingEvent = new Event().setId("event-no-attendees");

        Event patchedEvent = new Event().setId("event-no-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-no-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-no-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee(new AttendeeRequest("event-no-attendees", "ghost@gm2dev.com"));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-no-attendees"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getAttendees().isEmpty());
    }

    @Test
    void createEvent_omitsNullOptionalFieldsFromDescription() throws IOException {
        CalendarEventRequest request = new CalendarEventRequest(
                null, "Java", "Jane Doe", "jane@example.com",
                null, null, null, "interviewer@gm2dev.com",
                List.of(), START_TIME, END_TIME
        );

        Event createdEvent = new Event().setId("event-minimal");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        String desc = captor.getValue().getDescription();

        assertFalse(desc.contains("LinkedIn:"));
        assertFalse(desc.contains("Primary Area:"));
        assertFalse(desc.contains("Feedback Link:"));
    }
}
